<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
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
    if (!$q->fetch()) fail('This image is private.', 403);
}
$path = dirname(__DIR__, 2) . '/uploads/' . $media['owner_id'] . '/' . $id;
if (!is_file($path)) fail('Image not found.', 404);
header('Content-Type: ' . $media['mime']);
header('X-Content-Type-Options: nosniff');
header('Content-Length: ' . filesize($path));
readfile($path);
