import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { amount, senderName, rawNotification } = await req.json()

    // Validasi input
    if (!amount) {
      return new Response(
        JSON.stringify({ message: 'Amount is required' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    console.log(`Verifying payment for amount: ${amount} from ${senderName}`)

    // Create Supabase client
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Cari order yang cocok
    const { data: orders, error } = await supabase
      .from('orders')
      .select('*')
      .eq('status', 'PENDING')
      .eq('total', amount)
      .limit(1)

    if (error) {
      console.error('Database error:', error)
      return new Response(
        JSON.stringify({ message: 'Database error', error: error.message }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    if (!orders || orders.length === 0) {
      console.log('No matching pending order found.')
      return new Response(
        JSON.stringify({ message: 'No matching pending order found' }),
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
          senderName: senderName || 'Unknown',
          rawNotification: rawNotification || '',
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

    console.log(`Order ${order.id} successfully updated to PAID.`)

    return new Response(
      JSON.stringify({
        message: 'Payment verified successfully',
        orderId: order.id
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )

  } catch (error) {
    console.error('Error during payment verification:', error)
    return new Response(
      JSON.stringify({ message: 'Internal Server Error', error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})