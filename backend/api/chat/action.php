<?php
require dirname(__DIR__,2).'/includes/chat.php';
$d=input();$me=authenticated()['id'];$aid=entity_id($d,'actionId');$op=field($d,'operation',40,true);$p=$d['data']??null;
if(!is_array($p)) fail('Expected action data.');
$hash=hash('sha256',json_encode([$op,$p]));$db=db();$db->beginTransaction();
try {
    $db->query('SELECT id FROM content_lock WHERE id=1 FOR UPDATE')->fetch();
    $q=$db->prepare('SELECT * FROM action_receipts WHERE user_id=? AND action_id=?');$q->execute([$me,$aid]);
    if($old=$q->fetch()) {
        $db->rollBack();if($old['operation']!==$op || $old['request_hash']!==$hash) fail('Action ID was reused.',409);
        respond(['ok'=>true]);
    }
    $now=now_ms();$id=entity_id($p,'id');
    if($op==='messages.create') {
        $other=entity_id($p,'recipientId');
        $q=$db->prepare('SELECT id FROM users WHERE id=? AND profile_complete=1');$q->execute([$other]);
        if($other===$me || !$q->fetch()) fail('Recipient is unavailable.',404);
        $type=field($p,'type',12,true);$text=field($p,'text',10000);$ref=field($p,'imageUrl',42);
        if(!in_array($type,['text','image','video','file','post','story','system'],true)) fail('Unsupported message.');
        if(in_array($type,['image','video','file'],true)) {
            $q=$db->prepare('SELECT mime FROM media WHERE id=? AND owner_id=?');$q->execute([substr($ref,6),$me]);$mime=$q->fetchColumn();
            if(!preg_match('/^media:[a-f0-9-]{36}$/',$ref)||!$mime) fail('Upload the attachment first.',409);
            if($type==='image'&&!str_starts_with($mime,'image/')) fail('Invalid image.');
            if($type==='video'&&!str_starts_with($mime,'video/')) fail('Invalid video.');
        } elseif($type==='system') {
            if(($p['event']??'')!=='screenshot') fail('Invalid system message.');
            $q=$db->prepare('SELECT profile FROM users WHERE id=?');$q->execute([$me]);$u=json_decode($q->fetchColumn(),true);
            $text=($u['name']??'Someone').' took a screenshot';
        } elseif($type==='post') {
            $post=entity_id($p,'postId');accessible_post($post,$me);accessible_post($post,$other);
        } elseif($text==='') fail('Enter a message.');
        $body=['id'=>$id,'senderId'=>$me,'recipientId'=>$other,'type'=>$type,'text'=>$text,'imageUrl'=>$ref?:null,'timestamp'=>$now,'edited'=>false,'deleted'=>false,'hearts'=>(object)[]];
        foreach(['postId','replyToId','replyToText','replyToSender','event','fileName','mime'] as $k) if(isset($p[$k])) $body[$k]=field($p,$k,$k==='replyToText'?10000:255);
        if(!empty($body['replyToId'])) { $r=chat_member($body['replyToId'],$me);if(!in_array($other,[$r['sender_id'],$r['recipient_id']],true)) fail('Invalid reply.'); }
        $db->prepare('INSERT INTO messages VALUES (?,?,?,?,?)')->execute([$id,$me,$other,json_encode($body),$now]);
        notify_user($other,$me,$type==='system'?'screenshot':'message',$text?:('Sent a '.$type));
    } elseif(in_array($op,['messages.edit','messages.delete','messages.heart'],true)) {
        $r=chat_member($id,$me);$body=json_decode($r['body'],true);
        if($op==='messages.heart') {
            $hearts=(array)($body['hearts']??[]);
            if(($p['liked']??false)===true)$hearts[$me]=true;else unset($hearts[$me]);$body['hearts']=(object)$hearts;
        } else {
            if($r['sender_id']!==$me) fail('Only the sender can change a message.',403);
            if($now-(int)$r['created_at']>=300000) fail('Time limit passed. Messages can only be changed within five minutes.',409);
            if(in_array($body['type'],['system','call'],true)||($body['deleted']??false)) fail('This message cannot be changed.',409);
            if($op==='messages.edit') {$body['text']=field($p,'text',10000,true);$body['edited']=true;}
            else {$body['deleted']=true;$body['text']='Message deleted';$body['imageUrl']=null;$body['hearts']=(object)[];}
        }
        $db->prepare('UPDATE messages SET body=? WHERE id=?')->execute([json_encode($body),$id]);
    } elseif(in_array($op,['chat.read','chat.hide'],true)) {
        $other=entity_id($p,'other');$column=$op==='chat.read'?'read_at':'hidden_at';
        $at=min($now,max(0,(int)($p['through']??0)));
        $db->prepare("INSERT INTO chat_reads (owner_id,other_id,$column) VALUES (?,?,?) ON DUPLICATE KEY UPDATE $column=GREATEST($column,VALUES($column))")->execute([$me,$other,$at]);
    } elseif(in_array($op,['notifications.read','notifications.delete'],true)) {
        $q=$db->prepare('SELECT body FROM notifications WHERE id=? AND owner_id=?');$q->execute([$id,$me]);
        if($json=$q->fetchColumn()) {
            if($op==='notifications.delete')$db->prepare('DELETE FROM notifications WHERE id=? AND owner_id=?')->execute([$id,$me]);
            else {$n=json_decode($json,true);$n['read']=true;$db->prepare('UPDATE notifications SET body=? WHERE id=?')->execute([json_encode($n),$id]);}
        }
    } else fail('Unknown action.');
    $db->prepare('INSERT INTO action_receipts VALUES (?,?,?,?,?,?)')->execute([$me,$aid,$op,$hash,'{"ok":true}',$now]);
    $db->commit();respond(['ok'=>true]);
} catch(Throwable $e) {if($db->inTransaction())$db->rollBack();throw $e;}
