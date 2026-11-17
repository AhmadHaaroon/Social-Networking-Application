<?php
require dirname(__DIR__, 2) . '/includes/social.php';
$user = authenticated();
$term = field($_GET, 'q', 100);
$filter = field($_GET, 'filter', 20);
if (!in_array($filter, ['everyone', 'friends', 'requests'], true)) fail('Invalid search filter.');
$pattern = '%' . str_replace(['!', '%', '_'], ['!!', '!%', '!_'], $term) . '%';
$sql = "SELECT u.* FROM users u WHERE u.profile_complete = 1 AND u.id != ? AND (u.username LIKE ? ESCAPE '!' OR JSON_UNQUOTE(JSON_EXTRACT(u.profile, '$.name')) LIKE ? ESCAPE '!')";
$args = [$user['id'], $pattern, $pattern];
if ($filter !== 'everyone') {
    $sql .= ' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_a = ? AND f.user_b = u.id) OR (f.user_b = ? AND f.user_a = u.id)) AND f.state = ?)';
    array_push($args, $user['id'], $user['id'], $filter === 'friends' ? 'accepted' : 'pending');
}
$sql .= ' ORDER BY u.username LIMIT 100';
$q = db()->prepare($sql);
$q->execute($args);
$out = [];
foreach ($q as $row) $out[] = visible_user($row, $user['id']);
respond(['users' => $out]);
