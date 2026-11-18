"""Run against a disposable backend. Exercises server authorization, UUID retries and call signalling."""
import json
import os
import subprocess
import uuid
from smoke import call
from social_smoke import account, upload, download

def action(user, operation, data, expected=200, action_id=None):
    body=dict(actionId=action_id or str(uuid.uuid4()),operation=operation,data=data)
    call('chat/action.php',body,user['token'],expected)
    return body

def state(user):
    return call('social/state.php',token=user['token'])['content']

def main():
    suffix=uuid.uuid4().hex[:10]
    a,_=account('Sender',suffix);b,_=account('Receiver',suffix);c,_=account('Outsider',suffix)
    try:
        mid=str(uuid.uuid4())
        send=action(a,'messages.create',dict(id=mid,recipientId=b['user']['uid'],type='text',text='Hello',timestamp=1))
        call('chat/action.php',send,a['token'])
        assert sum(m['id']==mid for m in state(b)['messages'])==1
        assert not state(c)['messages']
        action(b,'messages.edit',dict(id=mid,text='Intrusion'),403)
        action(c,'messages.delete',dict(id=mid),404)
        action(a,'messages.edit',dict(id=mid,text='Edited'))
        m=next(m for m in state(b)['messages'] if m['id']==mid)
        assert m['edited'] and m['text']=='Edited' and m['timestamp']>1
        action(b,'messages.heart',dict(id=mid,liked=True))
        assert next(m for m in state(a)['messages'] if m['id']==mid)['hearts'][b['user']['uid']]
        call('chat/action.php',send,a['token'])
        assert next(m for m in state(b)['messages'] if m['id']==mid)['text']=='Edited'
        fid=str(uuid.uuid4());data=b'Kinnect attachment test'
        ref=upload(a,fid,data)['ref']
        action(a,'messages.create',dict(id=str(uuid.uuid4()),recipientId=b['user']['uid'],type='file',imageUrl=ref,fileName='notes.txt',mime='text/plain'))
        assert download(fid,b['token'])==data
        download(fid,c['token'],expected=403)
        sid=str(uuid.uuid4())
        action(b,'messages.create',dict(id=sid,recipientId=a['user']['uid'],type='system',event='screenshot',text='Forged name'))
        assert 'Forged' not in next(m for m in state(a)['messages'] if m['id']==sid)['text']
        assert any(n['type']=='screenshot' for n in state(a)['notifications'])
        socket=os.environ.get('KINNECT_TEST_SOCKET')
        if socket:
            sql="UPDATE messages SET created_at=1 WHERE id='%s'"%mid
            subprocess.run(['/Applications/XAMPP/xamppfiles/bin/mysql','--no-defaults','--socket='+socket,'-u','root','kinnect','-e',sql],check=True)
            action(a,'messages.edit',dict(id=mid,text='Too late'),409)
            action(a,'messages.delete',dict(id=mid),409)
        cid=str(uuid.uuid4())
        ring=dict(id=cid,operation='ring',other=b['user']['uid'],video=True)
        call('calls/action.php',ring,a['token']);call('calls/action.php',ring,a['token'])
        assert sum(x['callId']==cid for x in state(b)['calls'])==1
        call('calls/action.php',dict(id=cid,operation='accept'),c['token'],404)
        call('calls/action.php',dict(id=cid,operation='accept'),a['token'],409)
        call('calls/action.php',dict(id=cid,operation='accept'),b['token'])
        call('calls/token.php',dict(id=cid),c['token'],404)
        call('calls/action.php',dict(id=cid,operation='ended'),a['token'])
        call('calls/action.php',dict(id=cid,operation='ended'),b['token'])
        assert sum(m['id']==cid for m in state(a)['messages'])==1
        call('calls/token.php',dict(id=cid),a['token'],409)
        print('PASS: private chats, attachments, screenshot alerts, edits, reactions, retry deduplication, call authorization and logs'+(', five-minute enforcement.' if socket else '.'))
    finally:
        for u in (a,b,c):call('auth/logout.php',{},u['token'])
if __name__=='__main__':main()
