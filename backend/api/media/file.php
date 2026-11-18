<?php
require dirname(__DIR__, 2) . '/includes/content.php';
$me = authenticated()['id'];
$id = field($_GET, 'id', 36, true);
if (!preg_match('/^[a-f0-9-]{36}$/', $id)) fail('Invalid media ID.');
$q = db()->prepare('SELECT * FROM media WHERE id = ?');
$q->execute([$id]);
$media = $q->fetch();
if (!$media) fail('Image not found.', 404);
if ($media['owner_id'] !== $me) {
    $q = db()->prepare("SELECT id FROM users WHERE JSON_UNQUOTE(JSON_EXTRACT(profile, '$.photoUrl')) = ? OR JSON_UNQUOTE(JSON_EXTRACT(profile, '$.coverUrl')) = ? LIMIT 1");
    $q->execute(['media:' . $id, 'media:' . $id]);
    $allowed = (bool)$q->fetch();
    if (!$allowed) {
        foreach (db()->query('SELECT * FROM posts') as $post) {
            $body = json_decode($post['body'], true);
            if (in_array('media:'.$id, $body['images'] ?? [], true) && is_friend($me,$post['owner_id'])) { $allowed = true; break; }
        }
    }
    if (!$allowed) {
        $q = db()->prepare("SELECT p.owner_id FROM comments c JOIN posts p ON p.id = c.post_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.body, '$.imageUrl')) = ?");
        $q->execute(['media:'.$id]);
        foreach ($q as $post) if (is_friend($me,$post['owner_id'])) { $allowed = true; break; }
    }
    if (!$allowed) {
        $q = db()->prepare("SELECT * FROM stories WHERE JSON_UNQUOTE(JSON_EXTRACT(body, '$.mediaUrl')) = ? AND expires_at > ?");
        $q->execute(['media:'.$id,now_ms()]);
        foreach ($q as $story) if (can_story($story,$me)) { $allowed = true; break; }
    }
    if (!$allowed) {
        $q = db()->prepare("SELECT id FROM messages WHERE (sender_id=? OR recipient_id=?) AND JSON_UNQUOTE(JSON_EXTRACT(body, '$.imageUrl'))=? LIMIT 1");
        $q->execute([$me,$me,'media:'.$id]); $allowed=(bool)$q->fetch();
    }
    if (!$allowed) fail('This media is private or has expired.', 403);
}
$path = dirname(__DIR__, 2) . '/uploads/' . $media['owner_id'] . '/' . $id;
if (!is_file($path)) fail('Image not found.', 404);
header('Content-Type: ' . $media['mime']);
header('X-Content-Type-Options: nosniff');
header('Content-Disposition: attachment; filename="'.$id.'"');
header('Content-Length: ' . filesize($path));
readfile($path);
