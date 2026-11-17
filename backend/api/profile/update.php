<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
$d = input();
$user = authenticated();
$action = field($d, 'actionId', 36, true);
if (!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/', $action)) fail('Invalid action ID.');
$username = strtolower(field($d, 'username', 30, true));
if (!preg_match('/^[a-z0-9._]{3,30}$/', $username)) fail('Username must contain 3-30 letters, numbers, dots or underscores.');
$name = field($d, 'name', 161, true);
$bio = field($d, 'bio', 1000, !$user['profile_complete']);
$profile = [];
foreach (['website' => 500, 'phone' => 40, 'work' => 200, 'school' => 200, 'location' => 200, 'gender' => 20] as $key => $max) {
    $profile[$key] = field($d, $key, $max);
}
if (!in_array($profile['gender'], ['Male', 'Female', 'Custom'], true)) fail('Invalid gender.');
$profile['name'] = $name;
$profile['nameLower'] = mb_strtolower($name);
$parts = preg_split('/\s+/', $name, 2);
$profile['firstName'] = $parts[0];
$profile['lastName'] = $parts[1] ?? '';
$profile['bio'] = $bio;
$requestHash = hash('sha256', json_encode([$username, $profile]));
$db = db();
$db->beginTransaction();
try {
    // Lock the account so simultaneous retries cannot apply the same action twice.
    $q = $db->prepare('SELECT * FROM users WHERE id = ? FOR UPDATE');
    $q->execute([$user['id']]);
    $user = $q->fetch();
    $q = $db->prepare('SELECT * FROM action_receipts WHERE user_id = ? AND action_id = ?');
    $q->execute([$user['id'], $action]);
    $receipt = $q->fetch();
    if ($receipt) {
        $db->rollBack();
        if ($receipt['operation'] !== 'profile.update' || $receipt['request_hash'] !== $requestHash) fail('Action ID was reused with different content.', 409);
        respond(json_decode($receipt['response'], true));
    }
    $merged = array_merge(json_decode($user['profile'], true), $profile);
    $db->prepare('UPDATE users SET username = ?, profile = ?, profile_complete = 1 WHERE id = ?')
        ->execute([$username, json_encode($merged), $user['id']]);
    $user['username'] = $username;
    $user['profile'] = json_encode($merged);
    $user['profile_complete'] = true;
    $result = ['user' => public_user($user)];
    $db->prepare('INSERT INTO action_receipts VALUES (?, ?, ?, ?, ?, ?)')
        ->execute([$user['id'], $action, 'profile.update', $requestHash, json_encode($result), now_ms()]);
    $db->commit();
    respond($result);
} catch (PDOException $e) {
    $db->rollBack();
    if ($e->getCode() === '23000') fail('That username is taken. Choose another one.', 409);
    throw $e;
}
