#!/usr/bin/env python3
"""Compiles native Kotlin sources into app/classes4.dex using kotlinc and d8."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import rebuild

def compile_kotlin():
    kotlinc = shutil.which('kotlinc')
    d8 = shutil.which('d8')
    stubs_jar = ROOT / 'tools' / 'android-stubs.jar'
    
    if not kotlinc:
        print("Notice: kotlinc is not available on PATH; skipping Kotlin compilation.")
        return False
    if not d8:
        print("Notice: d8 is not available on PATH; skipping Kotlin compilation.")
        return False
    if not stubs_jar.is_file():
        subprocess.run([sys.executable, str(ROOT / 'tools' / 'make_android_stubs.py')], check=True)
    
    kt_sources = list((ROOT / 'native-src' / 'com' / 'agy' / 'ru').glob('*.kt'))
    if not kt_sources:
        print("No Kotlin sources found.")
        return False
        
    print(f"Compiling {len(kt_sources)} Kotlin source(s) with kotlinc...")
    with tempfile.TemporaryDirectory() as d:
        classes_dir = Path(d) / 'classes'
        classes_dir.mkdir(parents=True, exist_ok=True)
        
        # 1. Compile Kotlin files
        cmd_kotlinc = [
            kotlinc,
            '-cp', str(stubs_jar),
            '-d', str(classes_dir),
            '-jvm-target', '1.8'
        ] + [str(p) for p in kt_sources]
        subprocess.run(cmd_kotlinc, check=True)
        
        # 2. Also compile AppFileProvider.java into classes_dir
        with tempfile.TemporaryDirectory() as stubs_temp:
            afp_java = Path(stubs_temp) / 'AppFileProvider.java'
            afp_java.write_text(
                'package com.agy.util;\n'
                'import android.content.Context;\n'
                'import android.net.Uri;\n'
                'import java.io.File;\n'
                'public class AppFileProvider {\n'
                '    public static Uri getUriForFile(Context c, File f) {\n'
                '        try {\n'
                '            Class<?> fp = Class.forName("androidx.core.content.FileProvider");\n'
                '            return (Uri) fp.getMethod("getUriForFile", Context.class, String.class, File.class)\n'
                '                .invoke(null, c, c.getPackageName() + ".fileprovider", f);\n'
                '        } catch (Exception e) {\n'
                '            return Uri.parse("content://" + c.getPackageName() + ".fileprovider/" + f.getName());\n'
                '        }\n'
                '    }\n'
                '}\n'
            )
            subprocess.run(['javac', '-cp', str(stubs_jar), '-d', str(classes_dir), str(afp_java)], check=True)
        
        # 3. Translate compiled .class files into DEX with d8
        class_files = list(classes_dir.glob('**/*.class'))
        dex_out = Path(d) / 'dex'
        dex_out.mkdir(parents=True, exist_ok=True)
        cmd_d8 = [d8, '--output', str(dex_out), '--min-api', '26'] + [str(p) for p in class_files]
        subprocess.run(cmd_d8, check=True)
        
        out_dex = dex_out / 'classes.dex'
        if not out_dex.is_file():
            raise RuntimeError("d8 did not generate classes.dex")
            
        dex_bytes = out_dex.read_bytes()
        # Validate DEX structure
        rebuild.validate_dex(dex_bytes, 'classes4.dex')
        
        target = ROOT / 'app' / 'classes4.dex'
        target.write_bytes(dex_bytes)
        print(f"Successfully compiled Kotlin classes into {target} ({len(dex_bytes)} bytes)")
        return True

if __name__ == '__main__':
    compile_kotlin()
