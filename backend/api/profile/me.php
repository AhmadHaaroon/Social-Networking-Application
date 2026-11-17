<?php
require dirname(__DIR__, 2) . '/includes/bootstrap.php';
if ($_SERVER['REQUEST_METHOD'] !== 'GET') fail('Use GET for this endpoint.', 405);
respond(['user' => public_user(authenticated())]);
