<?php
require_once __DIR__ . '/social.php';

function is_friend(string $a, string $b): bool {
    if ($a === $b) return true;
    $ids = [$a, $b]; sort($ids, SORT_STRING);
    $q = db()->prepare("SELECT 1 FROM friendships WHERE user_a = ? AND user_b = ? AND state = 'accepted'");
    $q->execute($ids);
    return (bool) $q->fetchColumn();
}

function can_story(array $story, string $me): bool {
    if ($story['expires_at'] <= now_ms()) return false;
    if ($story['owner_id'] === $me) return true;
    if (!is_friend($me, $story['owner_id'])) return false;
    if ($story['audience'] !== 'close') return true;
    $q = db()->prepare('SELECT 1 FROM close_friends WHERE owner_id = ? AND friend_id = ?');
    $q->execute([$story['owner_id'], $me]);
    return (bool) $q->fetchColumn();
}

function content_state(string $me): array {
    $out = ['posts'=>[], 'comments'=>[], 'reactions'=>[], 'comment_likes'=>[], 'stories'=>[], 'story_views'=>[]];
    $posts = [];
    foreach (db()->query('SELECT * FROM posts ORDER BY created_at DESC') as $row) {
        if (!is_friend($me, $row['owner_id'])) continue;
        $out['posts'][] = json_decode($row['body'], true);
        $posts[$row['id']] = true;
    }
    $comments = [];
    foreach (db()->query('SELECT * FROM comments ORDER BY created_at') as $row) {
        if (!isset($posts[$row['post_id']])) continue;
        $out['comments'][] = json_decode($row['body'], true);
        $comments[$row['id']] = true;
    }
    foreach (db()->query('SELECT * FROM reactions') as $row) {
        if (isset($posts[$row['post_id']])) $out['reactions'][] = ['id'=>$row['post_id'].':'.$row['user_id'], 'postId'=>$row['post_id'], 'uid'=>$row['user_id'], 'key'=>$row['reaction']];
    }
    foreach (db()->query('SELECT * FROM comment_likes') as $row) {
        if (isset($comments[$row['comment_id']])) $out['comment_likes'][] = ['id'=>$row['comment_id'].':'.$row['user_id'], 'commentId'=>$row['comment_id'], 'uid'=>$row['user_id']];
    }
    $stories = [];
    $q = db()->prepare('SELECT * FROM stories WHERE expires_at > ? ORDER BY created_at');
    $q->execute([now_ms()]);
    foreach ($q as $row) {
        if (!can_story($row, $me)) continue;
        $out['stories'][] = json_decode($row['body'], true);
        if ($row['owner_id'] === $me) $stories[$row['id']] = true;
    }
    foreach (db()->query('SELECT * FROM story_views') as $row) {
        if (isset($stories[$row['story_id']])) $out['story_views'][] = ['id'=>$row['story_id'].':'.$row['user_id'], 'storyId'=>$row['story_id'], 'uid'=>$row['user_id'], 'timestamp'=>(int)$row['viewed_at']];
    }
    return $out;
}

function entity_id(array $data, string $key): string {
    $id = field($data, $key, 36, true);
    if (!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/', $id)) fail('Invalid '.$key.'.');
    return $id;
}

function owned_media(string $ref, string $me, bool $video = false): void {
    if (!preg_match('/^media:([a-f0-9-]{36})$/', $ref, $m)) fail('Invalid media reference.');
    $q = db()->prepare('SELECT mime FROM media WHERE id = ? AND owner_id = ?');
    $q->execute([$m[1], $me]);
    $mime = $q->fetchColumn();
    if (!$mime || (!str_starts_with($mime, 'image/') && !($video && str_starts_with($mime, 'video/')))) fail('Upload supported media before sharing.', 409);
}

function accessible_post(string $id, string $me): array {
    $q = db()->prepare('SELECT * FROM posts WHERE id = ?'); $q->execute([$id]);
    $row = $q->fetch();
    if (!$row) fail('This post no longer exists.', 404);
    if (!is_friend($me, $row['owner_id'])) fail('This post is only available to friends.', 403);
    return $row;
}
