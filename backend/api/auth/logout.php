<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
input();
$hash = hash('sha256', bearer());
$db = db();
$db->beginTransaction();
$q = $db->prepare('SELECT user_id FROM sessions WHERE token_hash = ? FOR UPDATE');
$q->execute([$hash]);
$uid = $q->fetchColumn();
$db->prepare('DELETE FROM devices WHERE session_hash = ?')->execute([$hash]);
$db->prepare('DELETE FROM sessions WHERE token_hash = ?')->execute([$hash]);
if ($uid) {
    $db->prepare('UPDATE users SET online = 0, last_seen = ? WHERE id = ?')->execute([now_ms(), $uid]);
}
$db->commit();
respond(['ok' => true]);
