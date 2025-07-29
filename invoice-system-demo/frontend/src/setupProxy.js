const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  console.log('setupProxy.js is being loaded!');
  
  // 代理所有 /api 请求到 Python 后端
  app.use(
    '/api',
    createProxyMiddleware({
      target: 'http://localhost:8000',
      changeOrigin: true,
      logLevel: 'debug',
      onProxyReq: (proxyReq, req, res) => {
        console.log('Proxying request:', req.method, req.url, '-> http://localhost:8000' + req.url);
      },
      onError: (err, req, res) => {
        console.error('Proxy error:', err);
      }
    })
  );
  
  // 代理 /data 请求到 Python 后端的静态文件
  app.use(
    '/data',
    createProxyMiddleware({
      target: 'http://localhost:8000',
      changeOrigin: true,
      logLevel: 'debug',
      onProxyReq: (proxyReq, req, res) => {
        console.log('Proxying data request:', req.method, req.url, '-> http://localhost:8000' + req.url);
      },
      onError: (err, req, res) => {
        console.error('Data proxy error:', err);
      }
    })
  );
};