<?php
require_once __DIR__ . '/bootstrap.php';

function visible_user(array $row, string $me): array {
    $u = public_user($row);
    if ($row['id'] !== $me) {
        foreach (['email', 'phone', 'birthday', 'fcmToken'] as $key) unset($u[$key]);
    }
    return $u;
}

function relations(string $me): array {
    $q = db()->prepare("SELECT f.*, EXISTS(SELECT 1 FROM close_friends c WHERE c.owner_id = ? AND c.friend_id = IF(f.user_a = ?, f.user_b, f.user_a)) AS is_close FROM friendships f WHERE state = 'accepted' OR user_a = ? OR user_b = ?");
    $q->execute([$me, $me, $me, $me]);
    $out = [];
    foreach ($q as $row) {
        $out[] = ['id' => $row['user_a'] . ':' . $row['user_b'], 'a' => $row['user_a'], 'b' => $row['user_b'],
            'requester' => $row['requester'], 'state' => $row['state'], 'timestamp' => (int) $row['updated_at'],
            'close' => (bool) $row['is_close']];
    }
    return $out;
}

function social_state(string $me): array {
    $q = db()->prepare('SELECT * FROM users WHERE profile_complete = 1 OR id = ? ORDER BY created_at DESC');
    $q->execute([$me]);
    $users = [];
    foreach ($q as $row) $users[] = visible_user($row, $me);
    return ['users' => $users, 'relations' => relations($me)];
}
