"""Run only against a disposable backend; creates test users and a tiny profile image."""
import base64
import json
import urllib.error
import urllib.request
import uuid

from smoke import BASE, call


def account(label, suffix):
    result = call('auth/signup.php', dict(email=label + suffix + '@example.com',
                  password='TestPassword42!', firstName=label, lastName='Social',
                  gender='Custom', birthday='2004-03-14'), expected=201)
    profile = dict(actionId=str(uuid.uuid4()), name=label + ' Social',
                   username=label.lower() + suffix, bio='Social test', gender='Custom')
    call('profile/update.php', profile, result['token'])
    return result, profile


def action(actor, other, kind, expected=200, action_id=None):
    body = dict(other=other['user']['uid'], kind=kind, actionId=action_id or str(uuid.uuid4()))
    call('friends/action.php', body, actor['token'], expected)
    return body['actionId']


def relation(actor, other):
    return next((r for r in call('social/state.php', token=actor['token'])['relations']
                 if {r['a'], r['b']} == {actor['user']['uid'], other['user']['uid']}), None)


def upload(actor, media_id, content, expected=200):
    boundary = 'KinnectTestBoundary'
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="id"\r\n\r\n{media_id}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(BASE + 'media/upload.php', data=body, headers={
        'Authorization': 'Bearer ' + actor['token'], 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (status, data)
    return json.loads(data)


def download(media_id, token=None, expected=200):
    request = urllib.request.Request(BASE + 'media/file.php?id=' + media_id,
                                     headers={'Authorization': 'Bearer ' + token} if token else {})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (status, data)
    return data


def main():
    suffix = uuid.uuid4().hex[:10]
    a, profile = account('Alpha', suffix)
    b, _ = account('Beta', suffix)
    c, _ = account('Gamma', suffix)
    try:
        found = call('users/search.php?q=Beta' + suffix + '&filter=everyone', token=a['token'])['users']
        assert [u['uid'] for u in found] == [b['user']['uid']]
        assert 'email' not in found[0] and 'phone' not in found[0]
        sent = action(a, b, 'send')
        action(a, b, 'send', action_id=sent)
        assert relation(b, a)['requester'] == a['user']['uid']
        assert len(call('users/search.php?q=&filter=requests', token=b['token'])['users']) == 1
        action(a, b, 'accept', expected=409)
        action(a, b, 'decline', expected=403)
        action(b, a, 'accept')
        assert relation(a, b)['state'] == 'accepted'
        assert len(call('users/search.php?q=&filter=friends', token=a['token'])['users']) == 1
        action(a, b, 'close')
        assert relation(a, b)['close'] and not relation(b, a)['close']
        action(a, b, 'unfriend')
        action(a, b, 'send', action_id=sent)  # Old retries cannot resurrect a removed relationship.
        assert relation(a, b) is None
        action(a, b, 'close', expected=409)
        action(a, b, 'send')
        action(b, a, 'decline')
        assert relation(a, b) is None
        action(a, c, 'send')
        action(a, c, 'cancel')
        assert relation(a, c) is None
        action(a, b, 'unfriend', expected=409, action_id=sent)
        image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ9sAAAAASUVORK5CYII=')
        media_id = str(uuid.uuid4())
        ref = upload(a, media_id, image)['ref']
        assert upload(a, media_id, image)['ref'] == ref
        upload(b, media_id, image, expected=409)
        attachment = upload(a, str(uuid.uuid4()), b'not an image')['ref']
        call('profile/update.php', dict(profile, actionId=str(uuid.uuid4()), photoUrl=attachment), a['token'], expected=409)
        download(media_id, expected=401)
        download(media_id, b['token'], expected=403)
        assert download(media_id, a['token']) == image
        call('profile/update.php', dict(profile, actionId=str(uuid.uuid4()), photoUrl=ref), b['token'], expected=409)
        call('profile/update.php', dict(profile, actionId=str(uuid.uuid4()), photoUrl=ref), a['token'])
        assert download(media_id, b['token']) == image
        print('PASS: friend states, permissions, filters, private fields, UUID replay, close friends,')
        print('      multipart images, upload replay, authenticated downloads and image ownership.')
    finally:
        for user in (a, b, c):
            call('auth/logout.php', {}, user['token'])


if __name__ == '__main__':
    main()
