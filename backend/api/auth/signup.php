<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
$d = input();
$email = strtolower(field($d, 'email', 254, true));
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Enter a valid email.');
$password = $d['password'] ?? '';
if (!is_string($password) || strlen($password) < 6 || strlen($password) > 72) fail('Use a password between 6 and 72 bytes.');
$first = field($d, 'firstName', 80, true);
$last = field($d, 'lastName', 80, true);
$gender = field($d, 'gender', 20);
if (!in_array($gender, ['Male', 'Female', 'Custom'], true)) fail('Invalid gender.');
$birthday = field($d, 'birthday', 10);
$date = DateTimeImmutable::createFromFormat('!Y-m-d', $birthday);
if (!$date || $date->format('Y-m-d') !== $birthday || $date > new DateTimeImmutable('today')) fail('Invalid birthday.');
$profile = ['firstName' => $first, 'lastName' => $last, 'name' => "$first $last",
    'nameLower' => mb_strtolower("$first $last"), 'gender' => $gender, 'birthday' => $birthday];
$id = uuid();
$db = db();
$db->beginTransaction();
try {
    $db->prepare('INSERT INTO users (id, email, password_hash, profile, created_at) VALUES (?, ?, ?, ?, ?)')
        ->execute([$id, $email, password_hash($password, PASSWORD_DEFAULT), json_encode($profile), now_ms()]);
    $q = $db->prepare('SELECT * FROM users WHERE id = ?');
    $q->execute([$id]);
    $result = session_for($q->fetch());
    $db->commit();
    respond($result, 201);
} catch (PDOException $e) {
    $db->rollBack();
    if ($e->getCode() === '23000') fail('An account with this email already exists. Try logging in.', 409);
    throw $e;
}
