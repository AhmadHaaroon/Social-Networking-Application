"""Run against a disposable Kinnect backend; creates two uniquely named test accounts."""
import json
import os
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('KINNECT_TEST_URL', 'http://localhost/kinnect/api/').rstrip('/') + '/'


def call(path, body=None, token=None, expected=200, header_name="Authorization", scheme="Bearer"):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers[header_name] = scheme + ' ' + token
    req = urllib.request.Request(BASE + path,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    result = json.loads(data)
    assert status == expected, (path, status, result.get('error'))
    return result


def main():
    suffix = uuid.uuid4().hex[:12]
    account = dict(email='smoke' + suffix + '@example.com', password='TestPassword42!',
                   firstName='Smoke', lastName='Test', gender='Custom', birthday='2004-03-14')
    assert call('health.php')['ok']
    call('profile/me.php', expected=401)
    first = call('auth/signup.php', account, expected=201)
    assert not first['user']['profileComplete']
    assert 'password_hash' not in first['user']
    call('auth/signup.php', account, expected=409)
    call('auth/login.php', dict(email=account['email'], password='wrong'), expected=401)
    logged = call('auth/login.php', dict(email=account['email'], password=account['password']))
    token = logged['token']
    assert len(token) == 64 and token != first['token']
    assert call('profile/me.php', token=token)['user']['uid'] == logged['user']['uid']
    assert call('profile/me.php', token=token, header_name='authorization', scheme='bearer')['user']['uid'] == logged['user']['uid']
    call('profile/me.php', token='0' * 64, expected=401)
    action = dict(actionId=str(uuid.uuid4()), name='Smoke Tester', username='smoke' + suffix,
                  bio='Initial biography', gender='Custom')
    saved = call('profile/update.php', action, token)
    assert saved['user']['profileComplete']
    assert call('profile/update.php', action, token) == saved
    altered = dict(action, bio='Different content using the same ID')
    call('profile/update.php', altered, token, expected=409)
    newer = dict(action, actionId=str(uuid.uuid4()), bio='New accepted biography')
    call('profile/update.php', newer, token)
    call('profile/update.php', action, token)
    assert call('profile/me.php', token=token)['user']['bio'] == 'New accepted biography'
    account['email'] = 'other' + suffix + '@example.com'
    second = call('auth/signup.php', account, expected=201)
    call('profile/update.php', dict(action, actionId=str(uuid.uuid4())), second['token'], expected=409)
    assert not call('profile/me.php', token=second['token'])['user']['profileComplete']
    call('auth/logout.php', {}, token)
    call('auth/logout.php', {}, token)  # A repeated logout is safe.
    call('profile/me.php', token=token, expected=401)
    call('auth/logout.php', {}, first['token'])
    call('auth/logout.php', {}, second['token'])
    print('PASS: health, authentication, duplicate email, wrong password, unique username,')
    print('      action replay, conflicting UUID, stale replay, account isolation and logout.')


if __name__ == '__main__':
    main()
