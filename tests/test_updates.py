import unittest, importlib.util, json, io, os, tempfile, zipfile, hashlib, struct, zlib
from unittest.mock import patch
from pathlib import Path
spec=importlib.util.spec_from_file_location('backend',Path(__file__).resolve().parents[1]/'tools/update_backend.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Response(io.BytesIO): pass
class Updates(unittest.TestCase):
 def release(self,variant='standard',tag='v3.0.7',digest=True):
  return {'tag_name':tag,'name':'Hibou 3.0.7 full release','assets':[{'name':'Hibou-3.0.7-'+v+'.apk','browser_download_url':m.DOWNLOAD_PREFIX+tag+'/Hibou-3.0.7-'+v+'.apk','size':120,'digest':('sha256:'+'1'*64) if digest else None} for v in ['standard','bypass']]}
 def http(self,obj):return lambda *a,**k:Response(json.dumps(obj).encode())
 def test_up_to_date(self):self.assertFalse(m.release_info('3.0.6','standard',self.http(self.release(tag='v3.0.6')))['available'])
 def test_no_downgrade(self):self.assertFalse(m.release_info('3.0.6','standard',self.http(self.release(tag='v3.0.5')))['available'])
 def test_numeric_versions(self):
  self.assertGreater(m.version_tuple('3.0.10'),m.version_tuple('3.0.9'))
  self.assertGreater(m.version_tuple('1.0.0-beta.2'),m.version_tuple('1.0.0-beta.1'))
  self.assertGreater(m.version_tuple('1.0.0'),m.version_tuple('1.0.0-beta.1'))
 def test_keep_bypass(self):self.assertTrue(m.release_info('3.0.6','bypass',self.http(self.release()))['url'].endswith('-bypass.apk'))
 def test_names_with_spaces(self):self.assertTrue(m.release_info('3.0.6','standard',self.http(self.release()))['url'].startswith('https://'))
 def test_unknown_variant(self):
  with self.assertRaises(ValueError):m.release_info('3.0.6','other',self.http(self.release()))
 def test_missing_variant(self):
  release=self.release();release['assets']=release['assets'][:1]
  with self.assertRaises(ValueError):m.release_info('3.0.6','bypass',self.http(release))
 def test_reject_foreign_url(self):
  release=self.release();release['assets'][0]['browser_download_url']='https://example.com/bad.apk'
  with self.assertRaises(ValueError):m.release_info('3.0.6','standard',self.http(release))
 def test_checksum_fallback(self):
  release=self.release(digest=False);release['assets'].append({'name':'SHA256SUMS.txt','browser_download_url':m.DOWNLOAD_PREFIX+'v3.0.7/SHA256SUMS.txt'})
  def http(req,**kw):return Response((('2'*64+'  Hibou-3.0.7-bypass.apk\n').encode()) if req.full_url.endswith('.txt') else json.dumps(release).encode())
  self.assertEqual(m.release_info('3.0.6','bypass',http)['sha256'],'2'*64)
 def test_missing_checksums(self):
  with self.assertRaises(ValueError):m.release_info('3.0.6','standard',self.http(self.release(digest=False)))
 def apk(self,broken=False):
  data=bytearray(112);data[:8]=b'dex\n035\0';struct.pack_into('<I',data,32,112);data[12:32]=hashlib.sha1(data[32:]).digest();struct.pack_into('<I',data,8,zlib.adler32(data[12:])&0xffffffff)
  if broken:data[12]=0
  buf=io.BytesIO()
  with zipfile.ZipFile(buf,'w') as z:z.writestr('AndroidManifest.xml',b'manifest');z.writestr('classes.dex',data)
  return buf.getvalue()
 def info(self,data):return {'url':m.DOWNLOAD_PREFIX+'v3.0.7/test-standard.apk','sha256':hashlib.sha256(data).hexdigest(),'size':len(data)}
 def test_verified_download(self):
  data=self.apk()
  with tempfile.TemporaryDirectory() as temp,patch.dict(os.environ,{'HOME':temp}):
   self.assertTrue(m.download(self.info(data),lambda *a,**k:Response(data))['ready']);self.assertEqual((Path(temp)/'updates/hibou-update.apk').read_bytes(),data);self.assertEqual((Path(temp)/'updates/leostryx-update.apk').read_bytes(),data)
 def test_truncated_download(self):
  data=self.apk()
  with tempfile.TemporaryDirectory() as temp,patch.dict(os.environ,{'HOME':temp}):
   with self.assertRaises(ValueError):m.download(self.info(data),lambda *a,**k:Response(data[:50]))
   self.assertFalse((Path(temp)/'updates/hibou-update.apk.part').exists());self.assertFalse((Path(temp)/'updates/hibou-update.sha256').exists())
 def test_reject_broken_dex(self):
  data=self.apk(True)
  with tempfile.TemporaryDirectory() as temp,patch.dict(os.environ,{'HOME':temp}):
   with self.assertRaises(ValueError):m.download(self.info(data),lambda *a,**k:Response(data))
   self.assertFalse((Path(temp)/'updates/hibou-update.apk').exists())
 def test_reject_hash_mismatch(self):
  data=self.apk();info=self.info(data);info['sha256']='f'*64
  with tempfile.TemporaryDirectory() as temp,patch.dict(os.environ,{'HOME':temp}):
   with self.assertRaises(ValueError):m.download(info,lambda *a,**k:Response(data))
class BuildConsistency(unittest.TestCase):
 def test_embedded_backend_matches_source(self):
  root=Path(__file__).resolve().parents[1]
  shell=(root/'app/assets/web/agy_tools.sh').read_text()
  embedded=shell.split("<<'HIBOU_UPDATE_PY'\n",1)[1].split('HIBOU_UPDATE_PY\n',1)[0]
  self.assertEqual(embedded,(root/'tools/update_backend.py').read_text())
if __name__=='__main__':unittest.main(verbosity=2)
