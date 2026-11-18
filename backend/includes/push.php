<?php
require_once __DIR__.'/bootstrap.php';

function push_http(string $url, string $body, array $headers): array {
    $c=curl_init($url);
    curl_setopt_array($c,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>$body,CURLOPT_HTTPHEADER=>$headers,CURLOPT_RETURNTRANSFER=>true,CURLOPT_CONNECTTIMEOUT=>10,CURLOPT_TIMEOUT=>20]);
    $response=curl_exec($c);$code=curl_getinfo($c,CURLINFO_HTTP_CODE);curl_close($c);
    if($response===false)throw new RuntimeException('Push service could not be reached.');
    return [$code,json_decode($response,true)??[]];
}

function push_access(array $key): string {
    static $token='', $until=0;
    if($until>time()+60)return $token;
    $b64=fn($s)=>rtrim(strtr(base64_encode($s),'+/','-_'),'=');
    $head=$b64(json_encode(['alg'=>'RS256','typ'=>'JWT']));
    $claims=$b64(json_encode(['iss'=>$key['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token','iat'=>time(),'exp'=>time()+3600]));
    if(!openssl_sign($head.'.'.$claims,$signature,$key['private_key'],OPENSSL_ALGO_SHA256))throw new RuntimeException('Invalid push signing key.');
    [$code,$result]=push_http('https://oauth2.googleapis.com/token',http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$head.'.'.$claims.'.'.$b64($signature)]),['Content-Type: application/x-www-form-urlencoded']);
    if($code!==200)throw new RuntimeException('Push authentication failed. Check the server service-account setup.');
    $token=$result['access_token'];$until=time()+(int)$result['expires_in'];return $token;
}

function push_drain(): void {
    $path=config()['fcm_service_account']??'';
    if(!$path||!is_file($path))return;
    $key=json_decode(file_get_contents($path),true,512,JSON_THROW_ON_ERROR);
    $access=push_access($key);
    foreach(db()->query('SELECT * FROM push_outbox ORDER BY created_at LIMIT 25') as $row){
        $data=json_decode($row['body'],true);$call=($data['type']??'')==='call';
        if(($call&&$row['created_at']<now_ms()-45000)||$row['created_at']<now_ms()-86400000){db()->prepare('DELETE FROM push_outbox WHERE id=?')->execute([$row['id']]);continue;}
        $q=db()->prepare('SELECT d.token FROM devices d JOIN sessions s ON s.token_hash=d.session_hash WHERE d.owner_id=? AND s.expires_at>?');$q->execute([$row['owner_id'],now_ms()]);
        $ok=true;
        foreach($q as $device){
            $payload=array_map(fn($v)=>(string)$v,$data);
            [$code,$result]=push_http('https://fcm.googleapis.com/v1/projects/'.rawurlencode($key['project_id']).'/messages:send',json_encode(['message'=>['token'=>$device['token'],'data'=>$payload,'android'=>['priority'=>'HIGH','ttl'=>$call?'45s':'86400s']]]),['Authorization: Bearer '.$access,'Content-Type: application/json']);
            if($code!==200){
                $unregistered=false;foreach($result['error']['details']??[] as $detail)if(($detail['errorCode']??'')==='UNREGISTERED')$unregistered=true;
                if($unregistered)db()->prepare('DELETE FROM devices WHERE token=?')->execute([$device['token']]);else $ok=false;
            }
        }
        if($ok)db()->prepare('DELETE FROM push_outbox WHERE id=?')->execute([$row['id']]);
        else db()->prepare('UPDATE push_outbox SET attempts=attempts+1 WHERE id=?')->execute([$row['id']]);
    }
}
