<?php
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require dirname(__DIR__).'/includes/push.php';
$lock=fopen(sys_get_temp_dir().'/kinnect-push-'.hash('sha256',__DIR__).'.lock','c');
if(!flock($lock,LOCK_EX|LOCK_NB))exit("A push worker is already running.\n");
if(empty(config()['fcm_service_account']))exit("Set fcm_service_account in backend/config.php first.\n");
echo "Kinnect push worker running. Stop with Ctrl+C.\n";
do{try{push_drain();}catch(Throwable $e){fwrite(STDERR,$e->getMessage()."\n");}if(in_array('--once',$argv,true))break;sleep(3);}while(true);
