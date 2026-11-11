const http = require('http');

const PORT = process.env.PORT || 8080;

const server = http.createServer(async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url === '/api/ai/generate' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', async () => {
      try {
        const apiKey = process.env.GEMINI_API_KEY;
        if (!apiKey) {
          console.error('[Server Error] GEMINI_API_KEY environment variable is not set!');
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'GEMINI_API_KEY environment variable is missing on server' }));
          return;
        }

        const parsed = JSON.parse(body || '{}');
        const prompt = parsed.prompt || 'Reply with OK';
        const modelName = parsed.modelName || 'gemini-3.5-flash-lite';

        console.log(`[Server] Received AI request: Model=${modelName}, PromptLength=${prompt.length}`);

        const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;

        const geminiResponse = await fetch(geminiUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [
              {
                parts: [{ text: prompt }]
              }
            ]
          })
        });

        const geminiData = await geminiResponse.json();

        if (!geminiResponse.ok) {
          console.error('[Server] Gemini API Error:', geminiData);
          res.writeHead(geminiResponse.status, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            error: geminiData.error?.message || 'Gemini API call failed'
          }));
          return;
        }

        const candidateText = geminiData.candidates?.[0]?.content?.parts?.[0]?.text || '';
        console.log(`[Server] Gemini API Success. Output length=${candidateText.length}`);

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ text: candidateText }));

      } catch (err) {
        console.error('[Server Exception]', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message || 'Internal server error' }));
      }
    });
  } else {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Endpoint not found' }));
  }
});

server.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`FocusTrace Gemini Proxy Server running on port ${PORT}`);
  console.log(`Endpoint: http://localhost:${PORT}/api/ai/generate`);
  console.log(`GEMINI_API_KEY status: ${process.env.GEMINI_API_KEY ? 'SET' : 'NOT SET'}`);
  console.log(`====================================================`);
});
