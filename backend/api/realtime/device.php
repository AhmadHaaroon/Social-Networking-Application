<?php
require dirname(__DIR__,2).'/includes/bootstrap.php';
$d=input();$me=authenticated()['id'];$token=field($d,'token',512,true);
db()->prepare('INSERT INTO devices VALUES (?,?,?) ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id),session_hash=VALUES(session_hash)')->execute([$token,$me,hash('sha256',bearer())]);
respond(['ok'=>true]);
