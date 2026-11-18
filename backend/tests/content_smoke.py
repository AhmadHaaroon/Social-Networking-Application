"""Run against a disposable backend. Optional KINNECT_TEST_SOCKET enables the expiry check."""
import base64
import json
import os
import subprocess
import uuid

from smoke import call
from social_smoke import account, action as friend_action, upload, download


def mutate(user, op, data, expected=200, action_id=None):
    body = dict(actionId=action_id or str(uuid.uuid4()), operation=op, data=data)
    call('content/action.php', body, user['token'], expected)
    return body


def state(user):
    return call('social/state.php', token=user['token'])['content']


def contains(user, kind, entity_id):
    return any(row['id'] == entity_id for row in state(user)[kind])


def main():
    suffix = uuid.uuid4().hex[:10]
    a, _ = account('Author', suffix)
    b, _ = account('Reader', suffix)
    c, _ = account('Other', suffix)
    try:
        friend_action(a, b, 'send')
        friend_action(b, a, 'accept')
        image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ9sAAAAASUVORK5CYII=')
        media_id = str(uuid.uuid4())
        ref = upload(a, media_id, image)['ref']
        post_id = str(uuid.uuid4())
        post = mutate(a, 'posts.create', dict(id=post_id, text='Multi-image post', images=[ref] * 5, timestamp=1))
        call('content/action.php', post, a['token'])
        assert sum(p['id'] == post_id for p in state(a)['posts']) == 1
        assert contains(b, 'posts', post_id) and not contains(c, 'posts', post_id)
        assert download(media_id, b['token']) == image
        download(media_id, c['token'], expected=403)
        mutate(c, 'posts.react', dict(id=post_id, key='like'), expected=403)
        mutate(b, 'posts.delete', dict(id=post_id), expected=403)
        for reaction in ['like', 'love', 'haha', 'wow', 'sad', 'angry']:
            mutate(b, 'posts.react', dict(id=post_id, key=reaction))
            rows = [r for r in state(a)['reactions'] if r['postId'] == post_id]
            assert len(rows) == 1 and rows[0]['key'] == reaction
        mutate(b, 'posts.react', dict(id=post_id, key=''))
        assert not [r for r in state(a)['reactions'] if r['postId'] == post_id]
        comment_id, reply_id = str(uuid.uuid4()), str(uuid.uuid4())
        comment = mutate(a, 'comments.create', dict(id=comment_id, postId=post_id, text='Photo comment', imageUrl=ref))
        mutate(b, 'comments.create', dict(id=reply_id, postId=post_id, text='Reply', parentId=comment_id))
        like = mutate(b, 'comments.like', dict(id=comment_id, liked=True))
        call('content/action.php', like, b['token'])
        assert sum(r['commentId'] == comment_id for r in state(a)['comment_likes']) == 1
        mutate(b, 'comments.delete', dict(id=comment_id), expected=403)
        mutate(a, 'comments.delete', dict(id=comment_id))
        assert not contains(a, 'comments', reply_id)
        call('content/action.php', comment, a['token'])
        assert not contains(a, 'comments', comment_id)
        friend_action(a, c, 'send')
        friend_action(c, a, 'accept')
        friend_action(a, b, 'close')
        story_id = str(uuid.uuid4())
        story = mutate(a, 'stories.create', dict(id=story_id, text='Close friends story', mediaType='text', audience='close', textX=.2, textY=.7, timestamp=1, expiresAt=2))
        assert contains(b, 'stories', story_id) and not contains(c, 'stories', story_id)
        saved = next(s for s in state(a)['stories'] if s['id'] == story_id)
        assert saved['expiresAt'] - saved['timestamp'] == 86400000 and saved['timestamp'] > 2
        mutate(c, 'stories.view', dict(id=story_id), expected=404)
        view = mutate(b, 'stories.view', dict(id=story_id))
        call('content/action.php', view, b['token'])
        assert sum(r['storyId'] == story_id for r in state(a)['story_views']) == 1
        assert not state(b)['story_views']
        video_path = os.environ.get('KINNECT_TEST_VIDEO')
        if video_path:
            with open(video_path, 'rb') as source:
                video = source.read()
            video_id = str(uuid.uuid4())
            video_ref = upload(a, video_id, video)['ref']
            video_story = str(uuid.uuid4())
            mutate(a, 'stories.create', dict(id=video_story, text='Video label', mediaType='video', mediaUrl=video_ref, audience='close'))
            assert contains(b, 'stories', video_story) and not contains(c, 'stories', video_story)
            assert download(video_id, b['token']) == video
            download(video_id, c['token'], expected=403)
            mutate(a, 'stories.delete', dict(id=video_story))
            assert not contains(b, 'stories', video_story)
        socket = os.environ.get('KINNECT_TEST_SOCKET')
        if socket:
            sql = "UPDATE stories SET expires_at=1, body=JSON_SET(body, '$.expiresAt', 1) WHERE id='%s';" % story_id
            subprocess.run(['/Applications/XAMPP/xamppfiles/bin/mysql', '--no-defaults', '--socket=' + socket,
                            '-u', 'root', 'kinnect', '-e', sql], check=True)
            assert not contains(a, 'stories', story_id) and not contains(b, 'stories', story_id)
            mutate(b, 'stories.view', dict(id=story_id), expected=404)
        mutate(a, 'posts.delete', dict(id=post_id))
        call('content/action.php', post, a['token'])
        assert not contains(a, 'posts', post_id)
        print('PASS: feed visibility, multi-image posts, all six reactions, removal, photo comments,')
        print('      replies, likes, ownership, cascading deletes, stale UUID retries, Close friends,')
        print('      unique views' + (', video upload/download' if video_path else '') + ', server timestamps' + (', and 24-hour expiry.' if socket else '.'))
    finally:
        for user in (a, b, c):
            call('auth/logout.php', {}, user['token'])


if __name__ == '__main__':
    main()
