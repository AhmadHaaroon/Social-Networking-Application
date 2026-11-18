<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
$me = authenticated()['id'];
if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail('Use POST.', 405);
$id = field($_POST, 'id', 36, true);
if (!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/', $id)) fail('Invalid media ID.');
$file = $_FILES['file'] ?? null;
if (!$file || $file['error'] !== UPLOAD_ERR_OK) fail('The media could not be uploaded. Check the server upload limit.', 413);
if ($file['size'] > 10 * 1024 * 1024) fail('Media exceeds 10 MB.', 413);
$mime = (new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
if (!$mime) $mime = 'application/octet-stream';
if (str_starts_with($mime, 'image/') && !getimagesize($file['tmp_name'])) fail('Choose a supported image.');
$hash = hash_file('sha256', $file['tmp_name']);
$db = db();
$db->beginTransaction();
try {
    $db->prepare('SELECT id FROM users WHERE id = ? FOR UPDATE')->execute([$me]);
    $q = $db->prepare('SELECT * FROM media WHERE id = ?');
    $q->execute([$id]);
    if ($existing = $q->fetch()) {
        $db->rollBack();
        if ($existing['owner_id'] !== $me || $existing['content_hash'] !== $hash) fail('Media ID was reused.', 409);
        respond(['ref' => 'media:' . $id]);
    }
    $dir = dirname(__DIR__, 2) . '/uploads/' . $me;
    if (!is_dir($dir) && !mkdir($dir, 0750, true)) throw new RuntimeException('Cannot create uploads directory.');
    if (!move_uploaded_file($file['tmp_name'], $dir . '/' . $id)) throw new RuntimeException('Cannot save uploaded media.');
    $db->prepare('INSERT INTO media VALUES (?, ?, ?, ?, ?, ?)')->execute([$id, $me, $mime, $hash, $file['size'], now_ms()]);
    $db->commit();
    respond(['ref' => 'media:' . $id]);
} catch (Throwable $e) {
    if ($db->inTransaction()) $db->rollBack();
    throw $e;
}
