<?php
require_once __DIR__ . '/content.php';

function notify_user(string $owner, string $from, string $type, string $text, array $extra = []): void {
    if ($owner === $from) return;
    $id = uuid();
    $n = array_merge(['id'=>$id,'type'=>$type,'fromUid'=>$from,'text'=>$text,'timestamp'=>now_ms(),'read'=>false],$extra);
    db()->prepare('INSERT INTO notifications VALUES (?, ?, ?)')->execute([$id,$owner,json_encode($n)]);
    $q=db()->prepare('SELECT profile FROM users WHERE id=?'); $q->execute([$from]);
    $profile=json_decode($q->fetchColumn(),true);
    $data=array_merge(['type'=>$type,'fromUid'=>$from,'toUid'=>$owner,'title'=>$profile['name'] ?? 'Kinnect','body'=>$text,'notificationId'=>$id],$extra);
    db()->prepare('INSERT INTO push_outbox (id,owner_id,body,created_at) VALUES (?,?,?,?)')->execute([$id,$owner,json_encode($data),now_ms()]);
}

function chat_state(string $me): array {
    $out=['messages'=>[],'chat_reads'=>[],'calls'=>[],'notifications'=>[]];
    $q=db()->prepare('SELECT * FROM messages WHERE sender_id=? OR recipient_id=? ORDER BY created_at,id');$q->execute([$me,$me]);
    foreach($q as $r) $out['messages'][]=json_decode($r['body'],true);
    $q=db()->prepare('SELECT * FROM chat_reads WHERE owner_id=? OR other_id=?');$q->execute([$me,$me]);
    foreach($q as $r) $out['chat_reads'][]=['id'=>$r['owner_id'].':'.$r['other_id'],'owner'=>$r['owner_id'],'other'=>$r['other_id'],'readAt'=>(int)$r['read_at'],'hiddenAt'=>(int)$r['hidden_at'],'typingUntil'=>(int)$r['typing_until']];
    $q=db()->prepare('SELECT * FROM calls WHERE caller_id=? OR callee_id=?');$q->execute([$me,$me]);
    foreach($q as $r) {
        $c=json_decode($r['body'],true);
        if (($c['status']==='ringing' && $c['timestamp']<now_ms()-45000) || ($c['status']==='accepted' && $r['updated_at']<now_ms()-90000)) {
            $c['status']=$c['status']==='ringing'?'missed':'ended';
            db()->prepare('UPDATE calls SET body=?,updated_at=? WHERE id=? AND updated_at=?')->execute([json_encode($c),now_ms(),$r['id'],$r['updated_at']]);
        }
        $out['calls'][]=$c;
    }
    $q=db()->prepare('SELECT body FROM notifications WHERE owner_id=?');$q->execute([$me]);
    foreach($q as $r) $out['notifications'][]=json_decode($r['body'],true);
    return $out;
}

function full_state(string $me): array {
    return array_merge(social_state($me),['content'=>array_merge(content_state($me),chat_state($me))]);
}

function chat_member(string $id, string $me): array {
    $q=db()->prepare('SELECT * FROM messages WHERE id=?');$q->execute([$id]);$r=$q->fetch();
    if (!$r || ($r['sender_id']!==$me && $r['recipient_id']!==$me)) fail('Message is unavailable.',404);
    return $r;
}
