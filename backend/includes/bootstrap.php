<?php
// Shared JSON responses, authentication and database access for the PHP endpoints.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(array $body, int $status = 200): never {
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function fail(string $message, int $status = 400): never {
    respond(['error' => $message], $status);
}

function config(): array {
    static $config;
    if ($config === null) {
        $path = dirname(__DIR__) . '/config.php';
        if (!is_file($path)) fail('Backend setup is incomplete: create config.php.', 503);
        $config = require $path;
    }
    return $config;
}

function db(): PDO {
    static $db;
    if ($db === null) {
        $c = config();
        $db = new PDO($c['dsn'], $c['user'], $c['password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    }
    return $db;
}

set_exception_handler(function (Throwable $e) {
    error_log('Kinnect: ' . $e->getMessage());
    fail('The server could not complete the request. Check the backend setup.', 500);
});

function now_ms(): int {
    return (int) floor(microtime(true) * 1000);
}

function uuid(): string {
    $b = random_bytes(16);
    $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
    $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    $h = bin2hex($b);
    return substr($h, 0, 8) . '-' . substr($h, 8, 4) . '-' . substr($h, 12, 4)
        . '-' . substr($h, 16, 4) . '-' . substr($h, 20);
}

function input(): array {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail('Use POST for this endpoint.', 405);
    $raw = file_get_contents('php://input');
    if (strlen($raw) > 65536) fail('Request too large.', 413);
    $data = json_decode($raw, true);
    if (!is_array($data)) fail('Expected a JSON object.');
    return $data;
}

function field(array $data, string $key, int $max, bool $required = false): string {
    $value = $data[$key] ?? '';
    if (!is_string($value)) fail('Invalid ' . $key . '.');
    $value = trim($value);
    if (($required && $value === '') || mb_strlen($value) > $max) fail('Invalid ' . $key . '.');
    return $value;
}

function bearer(): string {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    // Apache may expose Authorization through its request headers instead of $_SERVER.
    if ($header === '' && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strcasecmp($name, 'Authorization') === 0) {
                $header = $value;
                break;
            }
        }
    }
    if (!preg_match('/^Bearer[ \t]+([a-f0-9]{64})$/i', trim($header), $m)) fail('Please log in again.', 401);
    return $m[1];
}

function authenticated(): array {
    $q = db()->prepare('SELECT u.* FROM users u JOIN sessions s ON s.user_id = u.id WHERE s.token_hash = ? AND s.expires_at > ?');
    $q->execute([hash('sha256', bearer()), now_ms()]);
    $user = $q->fetch();
    if (!$user) fail('Your session has expired. Please log in again.', 401);
    return $user;
}

function public_user(array $row): array {
    $u = json_decode($row['profile'], true, 512, JSON_THROW_ON_ERROR);
    $u['uid'] = $row['id'];
    $u['email'] = $row['email'];
    $u['username'] = $row['username'] ?? '';
    $u['usernameLower'] = $u['username'];
    $u['profileComplete'] = (bool) $row['profile_complete'];
    $u['createdAt'] = (int) $row['created_at'];
    $u['online'] = (bool) $row['online'];
    $u['lastSeen'] = (int) $row['last_seen'];
    return $u;
}

function session_for(array $user): array {
    $token = bin2hex(random_bytes(32));
    $expiry = now_ms() + (int) config()['session_days'] * 86400000;
    db()->prepare('INSERT INTO sessions (token_hash, user_id, expires_at, last_seen) VALUES (?, ?, ?, ?)')
        ->execute([hash('sha256', $token), $user['id'], $expiry, now_ms()]);
    db()->prepare('UPDATE users SET online = 1, last_seen = ? WHERE id = ?')->execute([now_ms(), $user['id']]);
    $user['online'] = true;
    return ['token' => $token, 'expiresAt' => $expiry, 'user' => public_user($user)];
}
