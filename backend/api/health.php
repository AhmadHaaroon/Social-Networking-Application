<?php
require dirname(__DIR__) . '/includes/bootstrap.php';
db()->query('SELECT id FROM users LIMIT 1');
db()->query('SELECT id FROM content_lock LIMIT 1');
respond(['ok' => true, 'service' => 'Kinnect', 'stage' => 'feed-comments-stories']);
