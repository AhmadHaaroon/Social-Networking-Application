<?php
// Copy to config.php and use the MySQL credentials configured in XAMPP.
return [
    'dsn' => 'mysql:host=127.0.0.1;port=3306;dbname=kinnect;charset=utf8mb4',
    'user' => 'kinnect_app',
    'password' => 'CHANGE_ME',
    'session_days' => 30,
    // Keep the service-account file outside htdocs and outside the Android project.
    'fcm_service_account' => '',
    'agora_app_id' => '',
    'agora_app_certificate' => '',
];
