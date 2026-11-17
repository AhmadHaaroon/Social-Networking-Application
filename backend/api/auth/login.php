<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
$d = input();
$email = strtolower(field($d, 'email', 254, true));
$password = $d['password'] ?? '';
if (!is_string($password) || strlen($password) > 72) fail('Email or password is incorrect.', 401);
$q = db()->prepare('SELECT * FROM users WHERE email = ?');
$q->execute([$email]);
$user = $q->fetch();
if (!$user || !password_verify($password, $user['password_hash'])) fail('Email or password is incorrect.', 401);
respond(session_for($user));
