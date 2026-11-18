<?php
require dirname(__DIR__,2).'/includes/chat.php';
$d=input();$me=authenticated()['id'];$id=entity_id($d,'id');$op=field($d,'operation',12,true);$db=db();$now=now_ms();
$db->beginTransaction();
try {
    $db->query('SELECT id FROM content_lock WHERE id=1 FOR UPDATE')->fetch();
    $q=$db->prepare('SELECT * FROM calls WHERE id=?');$q->execute([$id]);$r=$q->fetch();
    if($op==='ring') {
        if(empty(config()['agora_app_id'])||empty(config()['agora_app_certificate']))fail('Agora setup is incomplete on the server.',503);
        $other=entity_id($d,'other');
        if($other===$me)fail('Invalid recipient.');
        if($r){if($r['caller_id']!==$me||$r['callee_id']!==$other)fail('Call ID was reused.',409);}
        else {
            $q=$db->prepare("SELECT body FROM calls WHERE (caller_id IN (?,?) OR callee_id IN (?,?)) AND updated_at>?");$q->execute([$me,$other,$me,$other,$now-90000]);
            foreach($q as $busy){$c=json_decode($busy['body'],true);if(($c['status']==='ringing'&&$c['timestamp']>$now-45000)||$c['status']==='accepted')fail('This user is already in a call.',409);}
            $c=['id'=>$id,'callId'=>$id,'callerUid'=>$me,'calleeUid'=>$other,'channel'=>$id,'video'=>($d['video']??false)===true,'status'=>'ringing','timestamp'=>$now,'duration'=>0];
            $db->prepare('INSERT INTO calls VALUES (?,?,?,?,?)')->execute([$id,$me,$other,json_encode($c),$now]);
            notify_user($other,$me,'call','Incoming '.($c['video']?'video':'voice').' call',['callId'=>$id,'channel'=>$id,'video'=>$c['video']?'true':'false','timestamp'=>(string)$now]);
        }
    } else {
        if(!$r||!in_array($me,[$r['caller_id'],$r['callee_id']],true))fail('Call unavailable.',404);
        $c=json_decode($r['body'],true);
        if(in_array($c['status'],['ringing','accepted'],true)) {
            if($op==='accept') {
                if($me!==$r['callee_id']||$c['timestamp']<$now-45000)fail('Call has expired.',409);
                if($c['status']==='ringing'){$c['status']='accepted';$c['acceptedAt']=$now;}
            } elseif($op==='heartbeat') {
                // Authenticated participants keep an active call lease alive.
            } elseif(in_array($op,['declined','ended','missed'],true)) {
                if($op==='declined'&&$me!==$r['callee_id'])fail('Only the receiver may decline.',403);
                $c['status']=$op;$c['duration']=isset($c['acceptedAt'])?(int)(($now-$c['acceptedAt'])/1000):0;
                $message=['id'=>$id,'senderId'=>$r['caller_id'],'recipientId'=>$r['callee_id'],'type'=>'call','text'=>($c['video']?'Video call':'Voice call').' · '.$op.($c['duration']?' · '.$c['duration'].'s':''),'timestamp'=>$now,'hearts'=>(object)[]];
                $db->prepare('INSERT IGNORE INTO messages VALUES (?,?,?,?,?)')->execute([$id,$r['caller_id'],$r['callee_id'],json_encode($message),$now]);
            } else fail('Invalid call action.');
            $db->prepare('UPDATE calls SET body=?,updated_at=? WHERE id=?')->execute([json_encode($c),$now,$id]);
        }
    }
    $db->commit();respond(['ok'=>true]);
}catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
