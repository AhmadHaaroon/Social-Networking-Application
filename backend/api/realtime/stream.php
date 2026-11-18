<?php
require dirname(__DIR__,2).'/includes/chat.php';
$user=authenticated();$me=$user['id'];$hash=hash('sha256',bearer());
header('Content-Type: text/event-stream');header('X-Accel-Buffering: no');
ini_set('zlib.output_compression','0');set_time_limit(40);
while(ob_get_level())ob_end_flush();
$last='';$end=time()+25;
do {
    if(connection_aborted())break;
    $q=db()->prepare('SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?');$q->execute([$hash,now_ms()]);
    if(!$q->fetchColumn())break;
    db()->prepare('UPDATE users SET online=1,last_seen=? WHERE id=?')->execute([now_ms(),$me]);
    $state=full_state($me);
    // Presence is boolean for live clients; retain lastSeen only for offline users to avoid needless refreshes.
    foreach($state['users'] as &$u)if($u['online'])$u['lastSeen']=0;unset($u);
    $json=json_encode($state,JSON_UNESCAPED_SLASHES);$digest=hash('sha256',$json);
    if($digest!==$last){echo "data: $json\n\n";$last=$digest;}else echo ": heartbeat\n\n";
    flush();sleep(2);
}while(time()<$end);
