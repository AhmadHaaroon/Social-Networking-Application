<?php
require dirname(__DIR__,2).'/includes/chat.php';
$d=input();$me=authenticated()['id'];$id=entity_id($d,'id');
$q=db()->prepare('SELECT * FROM calls WHERE id=?');$q->execute([$id]);$row=$q->fetch();
if(!$row||!in_array($me,[$row['caller_id'],$row['callee_id']],true))fail('Call unavailable.',404);
$call=json_decode($row['body'],true);
if(!in_array($call['status'],['ringing','accepted'],true)||($call['status']==='ringing'&&($me!==$row['caller_id']||$call['timestamp']<now_ms()-45000)))fail('Call is not active.',409);
$app=config()['agora_app_id']??'';$cert=config()['agora_app_certificate']??'';
if(!preg_match('/^[a-f0-9]{32}$/i',$app)||!preg_match('/^[a-f0-9]{32}$/i',$cert))fail('Agora setup is incomplete on the server.',503);
require dirname(__DIR__,2).'/vendor/agora/RtcTokenBuilder2.php';
$uid=$me===$row['caller_id']?1:2;
$token=RtcTokenBuilder2::buildTokenWithUid($app,$cert,$id,$uid,RtcTokenBuilder2::ROLE_PUBLISHER,3600,3600);
respond(['appId'=>$app,'token'=>$token,'uid'=>$uid,'channel'=>$id]);
