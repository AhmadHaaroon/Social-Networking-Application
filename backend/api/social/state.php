<?php
require dirname(__DIR__, 2) . '/includes/chat.php';
respond(full_state(authenticated()['id']));
