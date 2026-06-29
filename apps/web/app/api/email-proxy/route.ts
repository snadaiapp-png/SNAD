import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    
    // Verify bearer token
    const authHeader = request.headers.get('authorization') || '';
    const expectedToken = process.env.EMAIL_PROXY_BEARER_TOKEN || 'snad-proxy-2026';
    if (authHeader !== `Bearer ${expectedToken}`) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    // Forward to Resend API
    const resendResponse = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${process.env.RESEND_API_KEY || 're_QGJH9eHB_2o6cKpU8TDyvM7g4TJfCa8K6'}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from: body.from || 'onboarding@resend.dev',
        to: [body.destination],
        subject: body.subject,
        html: body.htmlBody,
      }),
    });

    if (!resendResponse.ok) {
      const errorText = await resendResponse.text();
      console.error('Resend API error:', errorText);
      return NextResponse.json({ error: 'Email delivery failed' }, { status: 502 });
    }

    const data = await resendResponse.json();
    return NextResponse.json({ success: true, id: data.id });
  } catch (error) {
    console.error('Email proxy error:', error);
    return NextResponse.json({ error: 'Internal error' }, { status: 500 });
  }
}
