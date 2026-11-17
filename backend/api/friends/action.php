<?php
require dirname(__DIR__, 2) . '/includes/social.php';
$d = input();
$me = authenticated()['id'];
$other = field($d, 'other', 36, true);
$action = field($d, 'actionId', 36, true);
$kind = field($d, 'kind', 20, true);
if (!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/', $action) || $me === $other) fail('Invalid friend action.');
if (!in_array($kind, ['send', 'accept', 'cancel', 'decline', 'unfriend', 'close', 'unclose'], true)) fail('Invalid friend action.');
$ids = [$me, $other];
sort($ids, SORT_STRING);
$hash = hash('sha256', json_encode([$other, $kind]));
$db = db();
$db->beginTransaction();
try {
    // Lock both accounts in stable order, including when the relationship does not exist yet.
    $q = $db->prepare('SELECT id, profile_complete FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE');
    $q->execute($ids);
    $users = $q->fetchAll();
    if (count($users) !== 2 || !$users[0]['profile_complete'] || !$users[1]['profile_complete']) {
        $db->rollBack();
        fail('Complete both profiles before adding friends.', 409);
    }
    $q = $db->prepare('SELECT * FROM action_receipts WHERE user_id = ? AND action_id = ?');
    $q->execute([$me, $action]);
    if ($receipt = $q->fetch()) {
        $db->rollBack();
        if ($receipt['operation'] !== 'friends.action' || $receipt['request_hash'] !== $hash) fail('Action ID was reused.', 409);
        respond(['ok' => true]);
    }
    $q = $db->prepare('SELECT * FROM friendships WHERE user_a = ? AND user_b = ?');
    $q->execute($ids);
    $f = $q->fetch();
    $pending = $f && $f['state'] === 'pending';
    $accepted = $f && $f['state'] === 'accepted';
    if ($kind === 'send' && !$f) {
        $db->prepare("INSERT INTO friendships VALUES (?, ?, ?, 'pending', ?)")->execute([$ids[0], $ids[1], $me, now_ms()]);
    } elseif ($kind === 'accept') {
        if (!$accepted && (!$pending || $f['requester'] === $me)) {
            $db->rollBack();
            fail('There is no incoming request to accept.', 409);
        }
        $db->prepare("UPDATE friendships SET state = 'accepted', updated_at = ? WHERE user_a = ? AND user_b = ?")
            ->execute([now_ms(), $ids[0], $ids[1]]);
    } elseif ($kind === 'cancel' || $kind === 'decline') {
        if ($pending && (($kind === 'cancel') !== ($f['requester'] === $me))) {
            $db->rollBack();
            fail('You cannot change this request.', 403);
        }
        if ($pending) $db->prepare('DELETE FROM friendships WHERE user_a = ? AND user_b = ?')->execute($ids);
    } elseif ($kind === 'unfriend') {
        if ($accepted) $db->prepare('DELETE FROM friendships WHERE user_a = ? AND user_b = ?')->execute($ids);
        $db->prepare('DELETE FROM close_friends WHERE (owner_id = ? AND friend_id = ?) OR (owner_id = ? AND friend_id = ?)')
            ->execute([$me, $other, $other, $me]);
    } elseif ($kind === 'close' || $kind === 'unclose') {
        if (!$accepted) {
            $db->rollBack();
            fail('Only friends can be added to Close friends.', 409);
        }
        if ($kind === 'close') $db->prepare('INSERT IGNORE INTO close_friends VALUES (?, ?)')->execute([$me, $other]);
        else $db->prepare('DELETE FROM close_friends WHERE owner_id = ? AND friend_id = ?')->execute([$me, $other]);
    }
    $db->prepare('INSERT INTO action_receipts VALUES (?, ?, ?, ?, ?, ?)')
        ->execute([$me, $action, 'friends.action', $hash, '{"ok":true}', now_ms()]);
    $db->commit();
    respond(['ok' => true]);
} catch (Throwable $e) {
    if ($db->inTransaction()) $db->rollBack();
    throw $e;
}
