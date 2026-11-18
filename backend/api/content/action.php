<?php
require dirname(__DIR__, 2) . '/includes/content.php';
$d = input(); $me = authenticated()['id'];
$action = entity_id($d, 'actionId'); $op = field($d, 'operation', 40, true);
$p = $d['data'] ?? null;
if (!is_array($p)) fail('Expected action data.');
$hash = hash('sha256', json_encode([$op, $p]));
$db = db(); $db->beginTransaction();
try {
    // Serialize content mutations so authorization, replies and cascading deletes agree.
    $db->query('SELECT id FROM content_lock WHERE id = 1 FOR UPDATE')->fetch();
    $q = $db->prepare('SELECT * FROM action_receipts WHERE user_id = ? AND action_id = ?'); $q->execute([$me, $action]);
    if ($old = $q->fetch()) {
        $db->rollBack();
        if ($old['operation'] !== $op || $old['request_hash'] !== $hash) fail('Action ID was reused.', 409);
        respond(['ok'=>true]);
    }
    $id = entity_id($p, 'id'); $now = now_ms();
    if ($op === 'posts.create') {
        $text = field($p, 'text', 10000); $images = $p['images'] ?? [];
        if (!is_array($images) || count($images) > 10 || (!$images && $text === '')) fail('Add text or up to ten images.');
        foreach ($images as $ref) { if (!is_string($ref)) fail('Invalid image.'); owned_media($ref, $me); }
        $bg = field($p, 'bgColor', 7);
        if ($bg !== '' && !preg_match('/^#[a-f0-9]{6}$/i', $bg)) fail('Invalid background colour.');
        $body = ['id'=>$id,'uid'=>$me,'text'=>$text,'images'=>$images,'bgColor'=>$images ? null : ($bg ?: null),'timestamp'=>$now,'shareCount'=>0];
        $db->prepare('INSERT INTO posts VALUES (?, ?, ?, ?)')->execute([$id,$me,json_encode($body),$now]);
    } elseif ($op === 'posts.delete' || $op === 'posts.react') {
        $post = accessible_post($id,$me);
        if ($op === 'posts.delete') {
            if ($post['owner_id'] !== $me) fail('Only the author can delete this post.',403);
            $db->prepare('DELETE FROM posts WHERE id = ?')->execute([$id]);
        } else {
            $key = field($p,'key',12);
            if (!in_array($key,['','like','love','haha','wow','sad','angry'],true)) fail('Invalid reaction.');
            if ($key === '') $db->prepare('DELETE FROM reactions WHERE post_id = ? AND user_id = ?')->execute([$id,$me]);
            else $db->prepare('INSERT INTO reactions VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE reaction = VALUES(reaction)')->execute([$id,$me,$key]);
        }
    } elseif ($op === 'comments.create') {
        $post = entity_id($p,'postId'); accessible_post($post,$me);
        $text = field($p,'text',5000); $image = field($p,'imageUrl',42);
        if ($text === '' && $image === '') fail('Add text or a photo.');
        if ($image !== '') owned_media($image,$me);
        $parent = field($p,'parentId',36); $reply = null;
        if ($parent !== '') {
            $q = $db->prepare('SELECT * FROM comments WHERE id = ? AND post_id = ?'); $q->execute([$parent,$post]); $row = $q->fetch();
            if (!$row || $row['parent_id'] !== null) fail('The comment being replied to no longer exists.',409);
            $reply = field($p,'replyToUid',36) ?: $row['owner_id'];
        }
        $body = ['id'=>$id,'postId'=>$post,'uid'=>$me,'text'=>$text,'imageUrl'=>$image ?: null,'parentId'=>$parent ?: null,'replyToUid'=>$reply,'timestamp'=>$now];
        $db->prepare('INSERT INTO comments VALUES (?, ?, ?, ?, ?, ?)')->execute([$id,$post,$me,$parent ?: null,json_encode($body),$now]);
    } elseif ($op === 'comments.delete' || $op === 'comments.like') {
        $q = $db->prepare('SELECT * FROM comments WHERE id = ?'); $q->execute([$id]); $row = $q->fetch();
        if (!$row) fail('This comment no longer exists.',404);
        accessible_post($row['post_id'],$me);
        if ($op === 'comments.delete') {
            if ($row['owner_id'] !== $me) fail('Only the author can delete this comment.',403);
            $db->prepare('DELETE FROM comments WHERE id = ?')->execute([$id]);
        } elseif (($p['liked'] ?? false) === true) $db->prepare('INSERT IGNORE INTO comment_likes VALUES (?, ?)')->execute([$id,$me]);
        else $db->prepare('DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?')->execute([$id,$me]);
    } elseif ($op === 'stories.create') {
        $text = field($p,'text',2000); $ref = field($p,'mediaUrl',42); $type = field($p,'mediaType',10,true); $audience = field($p,'audience',10,true);
        if (!in_array($type,['text','image','video'],true) || !in_array($audience,['public','close'],true)) fail('Invalid story.');
        if ($ref !== '') owned_media($ref,$me,$type === 'video');
        if (($type !== 'text' && $ref === '') || ($type === 'text' && $text === '')) fail('Add story content.');
        $body = ['id'=>$id,'uid'=>$me,'text'=>$text,'mediaUrl'=>$ref ?: null,'mediaType'=>$type,'audience'=>$audience,'timestamp'=>$now,'expiresAt'=>$now+86400000];
        foreach (['textX','textY'] as $axis) {
            $v = $p[$axis] ?? 0.5;
            if (!is_numeric($v) || $v < 0 || $v > 1) fail('Invalid text position.');
            $body[$axis] = (float)$v;
        }
        $db->prepare('INSERT INTO stories VALUES (?, ?, ?, ?, ?, ?)')->execute([$id,$me,$audience,json_encode($body),$now,$body['expiresAt']]);
    } elseif ($op === 'stories.delete' || $op === 'stories.view') {
        $q = $db->prepare('SELECT * FROM stories WHERE id = ?'); $q->execute([$id]); $row = $q->fetch();
        if (!$row || !can_story($row,$me)) fail('This story has expired or is unavailable.',404);
        if ($op === 'stories.delete') {
            if ($row['owner_id'] !== $me) fail('Only the author can delete this story.',403);
            $db->prepare('DELETE FROM stories WHERE id = ?')->execute([$id]);
        } elseif ($row['owner_id'] !== $me) $db->prepare('INSERT IGNORE INTO story_views VALUES (?, ?, ?)')->execute([$id,$me,$now]);
    } else fail('Unknown action.');
    $db->prepare('INSERT INTO action_receipts VALUES (?, ?, ?, ?, ?, ?)')->execute([$me,$action,$op,$hash,'{"ok":true}',$now]);
    $db->commit(); respond(['ok'=>true]);
} catch (Throwable $e) {
    if ($db->inTransaction()) $db->rollBack();
    throw $e;
}
