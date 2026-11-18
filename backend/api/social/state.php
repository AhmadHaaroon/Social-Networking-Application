<?php
require dirname(__DIR__, 2) . '/includes/content.php';
$user = authenticated();
respond(array_merge(social_state($user['id']), ['content'=>content_state($user['id'])]));
