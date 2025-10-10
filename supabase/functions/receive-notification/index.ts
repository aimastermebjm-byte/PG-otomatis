import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

// Pattern untuk parse notifikasi SMS banking
const BANK_PATTERNS = {
  BCA: {
    regex: /BCA.*(?:transfer|masuk|diterima).*Rp\s*([\d.]+)/i,
    senderRegex: /dari\s*([A-Z\s]+?)(?:\s+nomor|\s|$)/i
  },
  MANDIRI: {
    regex: /Mandiri.*(?:transfer|masuk|debit).*Rp\s*([\d.]+)/i,
    senderRegex: /dari\s*([A-Z\s]+?)(?:\s+nomor|\s|$)/i
  },
  BNI: {
    regex: /BNI.*(?:transfer|masuk|credit).*Rp\s*([\d.]+)/i,
    senderRegex: /dari\s*([A-Z\s]+?)(?:\s+nomor|\s|$)/i
  },
  BRI: {
    regex: /BRI.*(?:transfer|masuk|credit).*Rp\s*([\d.]+)/i,
    senderRegex: /dari\s*([A-Z\s]+?)(?:\s+nomor|\s|$)/i
  }
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const {
      senderNumber,
      message,
      timestamp,
      deviceId,
      bankType = 'AUTO', // BCA, MANDIRI, BNI, BRI, atau AUTO detect
      // New fields for app notifications
      bankName,
      senderName,
      amount,
      fullText
    } = await req.json()

    console.log(`Received notification: ${fullText || message}`)

    // Parse data dari notifikasi (baik dari SMS maupun app)
    let finalAmount = amount
    let finalSenderName = senderName
    let detectedBank = bankName || bankType

    // Jika data sudah lengkap dari app notification
    if (finalAmount && finalSenderName && bankName) {
      console.log(`Using parsed data from app: ${bankName} - Rp ${finalAmount} from ${finalSenderName}`)
    } else {
      // Parse dari SMS/text message jika data tidak lengkap
      const messageText = fullText || message

      // Detect bank type otomatis
      if (bankType === 'AUTO' && !bankName) {
        for (const [bank, patterns] of Object.entries(BANK_PATTERNS)) {
          if (messageText.toUpperCase().includes(bank)) {
            detectedBank = bank
            const amountMatch = messageText.match(patterns.regex)
            const senderMatch = messageText.match(patterns.senderRegex)

            if (amountMatch && !finalAmount) {
              // Clean amount dari format currency
              finalAmount = parseInt(amountMatch[1].replace(/\./g, ''))
            }

            if (senderMatch && !finalSenderName) {
              finalSenderName = senderMatch[1].trim()
            }

            break
          }
        }
      } else if (!bankName) {
        // Gunakan bank type yang spesifik
        const patterns = BANK_PATTERNS[bankType]
        if (patterns) {
          const amountMatch = messageText.match(patterns.regex)
          const senderMatch = messageText.match(patterns.senderRegex)

          if (amountMatch && !finalAmount) {
            finalAmount = parseInt(amountMatch[1].replace(/\./g, ''))
          }

          if (senderMatch && !finalSenderName) {
            finalSenderName = senderMatch[1].trim()
          }
        }
      }
    }

    if (!finalAmount) {
      console.log('No amount found in notification')
      return new Response(
        JSON.stringify({
          message: 'No payment amount detected',
          originalMessage: fullText || message
        }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Simpan notifikasi ke tabel notifications
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Simpan ke log notifications
    await supabase
      .from('notifications_log')
      .insert({
        sender_number: senderNumber,
        message: message,
        full_text: fullText,
        timestamp: timestamp || new Date().toISOString(),
        device_id: deviceId,
        bank_type: detectedBank,
        detected_amount: finalAmount,
        detected_sender: finalSenderName,
        processed: false
      })

    // Cari order yang cocok
    const { data: orders, error } = await supabase
      .from('orders')
      .select('*')
      .eq('status', 'PENDING')
      .eq('total', finalAmount)
      .limit(1)

    if (error) {
      console.error('Database error:', error)
      return new Response(
        JSON.stringify({ message: 'Database error', error: error.message }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    if (!orders || orders.length === 0) {
      console.log(`No matching pending order found for amount: Rp ${finalAmount}`)

      // Update log bahwa tidak ada order yang cocok
      await supabase
        .from('notifications_log')
        .update({
          processed: true,
          note: 'No matching order found'
        })
        .eq('timestamp', timestamp || new Date().toISOString())
        .limit(1)

      return new Response(
        JSON.stringify({
          message: 'No matching pending order found',
          amount: finalAmount,
          sender: finalSenderName
        }),
        { status: 404, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Update order yang ditemukan
    const order = orders[0]
    const { error: updateError } = await supabase
      .from('orders')
      .update({
        status: 'PAID',
        paidAt: new Date().toISOString(),
        paymentDetails: {
          senderName: finalSenderName || 'Unknown',
          senderNumber: senderNumber,
          rawMessage: fullText || message,
          bankType: detectedBank,
          deviceId: deviceId
        }
      })
      .eq('id', order.id)

    if (updateError) {
      console.error('Update error:', updateError)
      return new Response(
        JSON.stringify({ message: 'Update error', error: updateError.message }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Update log notification
    await supabase
      .from('notifications_log')
      .update({
        processed: true,
        order_id: order.id,
        note: 'Payment verified successfully'
      })
      .eq('timestamp', timestamp || new Date().toISOString())
      .limit(1)

    console.log(`✅ Order ${order.id} successfully updated to PAID. Amount: Rp ${finalAmount}`)

    // Trigger real-time update (Supabase otomatis broadcast lewat realtime subscription)

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Payment verified successfully',
        orderId: order.id,
        amount: finalAmount,
        sender: finalSenderName,
        bank: detectedBank
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )

  } catch (error) {
    console.error('Error processing notification:', error)
    return new Response(
      JSON.stringify({ message: 'Internal Server Error', error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})