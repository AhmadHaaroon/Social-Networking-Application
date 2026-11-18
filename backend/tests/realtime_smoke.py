"""Disposable-backend check: persistent event stream, presence, and participant-bound Agora tokens."""
import json
import queue
import threading
import time
import urllib.request
import uuid
from smoke import BASE,call
from social_smoke import account
from chat_smoke import action

def main():
    suffix=uuid.uuid4().hex[:10]
    a,_=account('StreamA',suffix);b,_=account('StreamB',suffix)
    events=queue.Queue()
    req=urllib.request.Request(BASE+'realtime/stream.php',headers={'Authorization':'Bearer '+b['token']})
    response=urllib.request.urlopen(req,timeout=12)
    def read():
        try:
            for line in response:
                if line.startswith(b'data: '):events.put(json.loads(line[6:]))
        except Exception as e:events.put(e)
    thread=threading.Thread(target=read,daemon=True);thread.start()
    try:
        first=events.get(timeout=10)
        assert not isinstance(first,Exception),first
        assert any(u['uid']==b['user']['uid'] and u['online'] for u in first['users'])
        mid=str(uuid.uuid4())
        action(a,'messages.create',dict(id=mid,recipientId=b['user']['uid'],type='text',text='Persistent stream delivery'))
        deadline=time.time()+10;seen=False
        while time.time()<deadline:
            state=events.get(timeout=10)
            if any(m['id']==mid for m in state['content']['messages']):seen=True;break
        assert seen
        cid=str(uuid.uuid4())
        call('calls/action.php',dict(id=cid,operation='ring',other=b['user']['uid']),a['token'])
        caller=call('calls/token.php',dict(id=cid),a['token'])
        assert caller['uid']==1 and caller['token'].startswith('007') and caller['channel']==cid
        call('calls/token.php',dict(id=cid),b['token'],409)
        call('calls/action.php',dict(id=cid,operation='accept'),b['token'])
        receiver=call('calls/token.php',dict(id=cid),b['token'])
        assert receiver['uid']==2 and receiver['token'].startswith('007')
        call('calls/action.php',dict(id=cid,operation='ended'),a['token'])
        print('PASS: persistent stream delivers new messages, presence updates, and Agora tokens are bound to call participants.')
    finally:
        for u in (a,b):call('auth/logout.php',{},u['token'])
        response.close()
if __name__=='__main__':main()
