\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database() <> 'g1_restore_cert_20261104_v3'
 OR (SELECT system_identifier::text FROM pg_control_system()) <> '7703842253085562712'
 OR inet_server_port() <> 55474
 THEN RAISE EXCEPTION 'BLOCKED wrong cluster or database'; END IF;
END $guard$;
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261104_1__login_tenant_index_transfer_consistency.sql
DO $test$
DECLARE
 a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); u uuid:=gen_random_uuid();
 e1 text:='g1a-'||u||'@example.invalid'; e2 text:='g1b-'||u||'@example.invalid';
 n1 text:='g1a_'||replace(u::text,'-',''); n2 text:='g1b_'||replace(u::text,'-','');
 c1 integer; c2 integer;
BEGIN
 INSERT INTO public.tenants(id,name,subdomain,status,created_at,updated_at)
 VALUES(a,'G1 A','g1a-'||substr(replace(a::text,'-',''),1,24),'ACTIVE',now(),now()),
       (b,'G1 B','g1b-'||substr(replace(b::text,'-',''),1,24),'ACTIVE',now(),now());
 INSERT INTO public.users(id,tenant_id,email,username,status,created_at,updated_at)
 VALUES(u,a,e1,n1,'ACTIVE',now(),now());
 SELECT count(*) INTO c1 FROM public.login_tenant_index WHERE tenant_id=a AND identifier IN(e1,n1);
 IF c1 <> 2 THEN RAISE EXCEPTION 'EXACT_MIGRATION_INSERT=FAIL %',c1; END IF;
 RAISE NOTICE 'EXACT_MIGRATION_INSERT=PASS';
 UPDATE public.users SET email=e2,username=n2,updated_at=now() WHERE id=u;
 SELECT count(*) INTO c1 FROM public.login_tenant_index WHERE tenant_id=a AND identifier IN(e1,n1);
 SELECT count(*) INTO c2 FROM public.login_tenant_index WHERE tenant_id=a AND identifier IN(e2,n2);
 IF c1<>0 OR c2<>2 THEN RAISE EXCEPTION 'EXACT_MIGRATION_UPDATE=FAIL old=% new=%',c1,c2;END IF;
 RAISE NOTICE 'EXACT_MIGRATION_UPDATE=PASS';
 UPDATE public.users SET tenant_id=b,updated_at=now() WHERE id=u;
 SELECT count(*) INTO c1 FROM public.login_tenant_index WHERE tenant_id=a AND identifier IN(e2,n2);
 SELECT count(*) INTO c2 FROM public.login_tenant_index WHERE tenant_id=b AND identifier IN(e2,n2);
 IF c1<>0 OR c2<>2 THEN RAISE EXCEPTION 'EXACT_MIGRATION_TRANSFER=FAIL old=% new=%',c1,c2;END IF;
 RAISE NOTICE 'EXACT_MIGRATION_TRANSFER=PASS';
 DELETE FROM public.users WHERE id=u;
 SELECT count(*) INTO c1 FROM public.login_tenant_index WHERE tenant_id IN(a,b) AND identifier IN(e1,e2,n1,n2);
 IF c1<>0 THEN RAISE EXCEPTION 'EXACT_MIGRATION_DELETE=FAIL %',c1;END IF;
 RAISE NOTICE 'EXACT_MIGRATION_DELETE=PASS';
END $test$;
ROLLBACK;