<?php
require dirname(__DIR__, 2) . '/includes/social.php';
$user = authenticated();
respond(social_state($user['id']));
