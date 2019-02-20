from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
import sys
class WebServerHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith("/index.html"):
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            f = open('index.html', 'rb')
            self.wfile.write(f.read())
            f.close()
            return
        elif self.path.endswith("/myimage.png"):
            self.send_response(200)
            self.send_header('Content-type', 'image/png')
            self.end_headers()
            img = open('myimage.png', 'rb')
            self.wfile.write(img.read())
            img.close()
            return
        else:
            self.send_error(404, 'File not found')

def main():
    try:
        port = int(sys.argv[1])
        server = HTTPServer(('', port), WebServerHandler)
        server.serve_forever()
    except KeyboardInterrupt:
        server.socket.close()
    
if __name__ == '__main__':
    main()
