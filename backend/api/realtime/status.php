<?php
require dirname(__DIR__,2).'/includes/bootstrap.php';
$d=input();$me=authenticated()['id'];
if(isset($d['online'])) db()->prepare('UPDATE users SET online=?,last_seen=? WHERE id=?')->execute([($d['online']===true)?1:0,now_ms(),$me]);
if(isset($d['other'])) {
    $other=field($d,'other',36,true);
    db()->prepare('INSERT INTO chat_reads (owner_id,other_id,typing_until) VALUES (?,?,?) ON DUPLICATE KEY UPDATE typing_until=VALUES(typing_until)')->execute([$me,$other,($d['typing']??false)?now_ms()+6000:0]);
}
respond(['ok'=>true]);
