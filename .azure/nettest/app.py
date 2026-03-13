#!/usr/bin/env python3
"""Simple network test web UI for Azure Container Apps.
Tests DNS, HTTP, HTTPS, and TCP connectivity to any target from inside the container."""

import http.server
import subprocess
import socketserver
import json
import urllib.parse
import os

HTML = '''<!DOCTYPE html>
<html>
<head>
<title>Container Network Tester</title>
<style>
  body { font-family: -apple-system, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; background: #1e1e1e; color: #d4d4d4; }
  h1 { color: #569cd6; }
  input, select, button { padding: 8px 12px; margin: 4px; border: 1px solid #555; border-radius: 4px; background: #2d2d2d; color: #d4d4d4; font-size: 14px; }
  input[type=text] { width: 300px; }
  button { background: #0e639c; border-color: #0e639c; color: white; cursor: pointer; }
  button:hover { background: #1177bb; }
  pre { background: #252526; padding: 16px; border-radius: 4px; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; border: 1px solid #333; }
  .success { color: #4ec9b0; }
  .error { color: #f44747; }
  .info { color: #9cdcfe; }
  #loading { display: none; color: #dcdcaa; }
  .section { margin: 20px 0; padding: 16px; background: #252526; border-radius: 4px; border: 1px solid #333; }
</style>
</head>
<body>
<h1>Container Network Tester</h1>
<p class="info">Running inside: Azure Container Apps (DEV-AKS-MANAGED-ENVIROMENT01)</p>

<div class="section">
  <h3>Quick Test</h3>
  <input type="text" id="target" placeholder="hostname or IP (e.g. keystonedev.revfcu.com)" value="keystonedev.revfcu.com">
  <input type="text" id="port" placeholder="port" value="8443" style="width:80px">
  <select id="testType">
    <option value="all">All Tests</option>
    <option value="dns">DNS Only</option>
    <option value="curl">HTTP/HTTPS Curl</option>
    <option value="tcp">TCP Connect</option>
    <option value="traceroute">Traceroute</option>
    <option value="ping">Ping</option>
  </select>
  <button onclick="runTest()">Test</button>
  <span id="loading">Running...</span>
</div>

<div class="section">
  <h3>Results</h3>
  <pre id="results">Enter a target and click Test.</pre>
</div>

<div class="section">
  <h3>Container Info</h3>
  <pre id="info">Loading...</pre>
</div>

<script>
async function runTest() {
  const target = document.getElementById('target').value.trim();
  const port = document.getElementById('port').value.trim() || '443';
  const testType = document.getElementById('testType').value;
  if (!target) { alert('Enter a target'); return; }
  document.getElementById('loading').style.display = 'inline';
  document.getElementById('results').textContent = 'Running tests...';
  try {
    const r = await fetch('/test?' + new URLSearchParams({target, port, type: testType}));
    document.getElementById('results').innerHTML = await r.text();
  } catch(e) {
    document.getElementById('results').textContent = 'Error: ' + e;
  }
  document.getElementById('loading').style.display = 'none';
}

fetch('/info').then(r => r.text()).then(t => document.getElementById('info').innerHTML = t);
</script>
</body>
</html>'''


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)

        if parsed.path == '/test':
            params = urllib.parse.parse_qs(parsed.query)
            target = params.get('target', [''])[0]
            port = params.get('port', ['443'])[0]
            test_type = params.get('type', ['all'])[0]
            result = self.run_tests(target, port, test_type)
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.end_headers()
            self.wfile.write(result.encode())

        elif parsed.path == '/info':
            result = self.get_info()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.end_headers()
            self.wfile.write(result.encode())

        else:
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.end_headers()
            self.wfile.write(HTML.encode())

    def run_cmd(self, cmd, timeout=15):
        try:
            r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
            output = r.stdout + r.stderr
            return output.strip(), r.returncode
        except subprocess.TimeoutExpired:
            return f"TIMEOUT after {timeout}s", 1
        except Exception as e:
            return f"ERROR: {e}", 1

    def colorize(self, label, output, code):
        cls = 'success' if code == 0 else 'error'
        return f'<span class="info">=== {label} ===</span>\n<span class="{cls}">{self.escape(output)}</span>\n\n'

    def escape(self, s):
        return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

    def run_tests(self, target, port, test_type):
        results = ''

        if test_type in ('all', 'dns'):
            out, code = self.run_cmd(f'nslookup {target}')
            results += self.colorize('DNS Resolution', out, code)

        if test_type in ('all', 'curl'):
            # HTTPS
            out, code = self.run_cmd(
                f'curl -sk -o /dev/null -w "HTTP %{{http_code}} | Connect: %{{time_connect}}s | Total: %{{time_total}}s" '
                f'--connect-timeout 10 https://{target}:{port}/')
            results += self.colorize(f'HTTPS :{port}', out, code)

            # HTTP
            out, code = self.run_cmd(
                f'curl -sk -o /dev/null -w "HTTP %{{http_code}} | Connect: %{{time_connect}}s | Total: %{{time_total}}s" '
                f'--connect-timeout 10 http://{target}/')
            results += self.colorize('HTTP :80', out, code)

        if test_type in ('all', 'tcp'):
            out, code = self.run_cmd(f'timeout 5 bash -c "echo >/dev/tcp/{target}/{port}" 2>&1 '
                                     f'&& echo "TCP connection to {target}:{port} SUCCEEDED" '
                                     f'|| echo "TCP connection to {target}:{port} FAILED"')
            # Fallback if bash tcp not available
            if 'not found' in out or 'syntax' in out.lower():
                out, code = self.run_cmd(f'nc -zv -w 5 {target} {port}')
            results += self.colorize(f'TCP :{port}', out, code)

        if test_type in ('traceroute',):
            out, code = self.run_cmd(f'traceroute -m 15 -w 2 {target}', timeout=30)
            results += self.colorize('Traceroute', out, code)

        if test_type in ('ping',):
            out, code = self.run_cmd(f'ping -c 4 -W 2 {target}')
            results += self.colorize('Ping', out, code)

        return results

    def get_info(self):
        lines = []
        out, _ = self.run_cmd('hostname')
        lines.append(f'Hostname: {out}')
        out, _ = self.run_cmd('cat /etc/resolv.conf')
        lines.append(f'\n<span class="info">=== /etc/resolv.conf ===</span>\n{self.escape(out)}')
        out, _ = self.run_cmd('ip route 2>/dev/null || route -n 2>/dev/null || echo "no route cmd"')
        lines.append(f'\n<span class="info">=== Routes ===</span>\n{self.escape(out)}')
        out, _ = self.run_cmd('ip addr 2>/dev/null || ifconfig 2>/dev/null || echo "no ip cmd"')
        lines.append(f'\n<span class="info">=== Network Interfaces ===</span>\n{self.escape(out)}')
        return '\n'.join(lines)

    def log_message(self, format, *args):
        pass  # suppress request logging


if __name__ == '__main__':
    port = int(os.environ.get('PORT', '80'))
    with socketserver.TCPServer(('', port), Handler) as server:
        print(f'Network tester serving on :{port}', flush=True)
        server.serve_forever()
