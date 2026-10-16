import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/api/payments/disconnect':
            self.close_connection = True
            return
        if self.path.startswith('/api/screens/'):
            screen_id = int(self.path.rsplit('/', 1)[1])
            data = {'id': screen_id, 'cinemaId': 1, 'status': 'ACTIVE'}
            if screen_id == 404:
                return self.respond(404, None)
            if screen_id == 503:
                return self.respond(503, None)
            if screen_id == 2:
                data['cinemaId'] = 2
            if screen_id == 3:
                data['status'] = 'INACTIVE'
        elif self.path.startswith('/api/cinemas/'):
            data = {'id': 1, 'location': 'Ha Noi', 'status': 'ACTIVE'}
        else:
            data = {'method': self.command, 'path': self.path, 'headers': dict(self.headers)}
        self.respond(200, data)

    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length', 0)))
        self.respond(200, {'method': self.command, 'path': self.path, 'headers': dict(self.headers)})

    def respond(self, status, data):
        body = json.dumps({'status': status, 'message': 'Contract fixture', 'data': data}).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


ThreadingHTTPServer(('0.0.0.0', 8090), Handler).serve_forever()
