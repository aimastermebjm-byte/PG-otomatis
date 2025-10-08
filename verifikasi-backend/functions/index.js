const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Inisialisasi Firebase Admin SDK
admin.initializeApp();

// Firestore reference
const db = admin.firestore();

// --- FUNGSI VERIFIKASI PEMBAYARAN ---
exports.verifyPayment = functions.https.onRequest(async (req, res) => {
  // Hanya izinkan metode POST
  if (req.method !== 'POST') {
    return res.status(405).send('Method Not Allowed');
  }

  try {
    const { amount, senderName, rawNotification } = req.body;

    // Validasi input sederhana
    if (!amount) {
      return res.status(400).send({ message: 'Amount is required' });
    }

    console.log(`Verifying payment for amount: ${amount} from ${senderName}`);

    // Cari order yang cocok
    const ordersRef = db.collection('orders');
    const snapshot = await ordersRef
      .where('status', '==', 'PENDING')
      .where('total', '==', amount)
      .limit(1) // Cukup ambil satu yang paling cocok
      .get();

    if (snapshot.empty) {
      console.log('No matching pending order found.');
      return res.status(404).send({ message: 'No matching pending order found' });
    }

    // Update order yang ditemukan
    const orderDoc = snapshot.docs[0];
    const orderId = orderDoc.id;

    await orderDoc.ref.update({
      status: 'PAID',
      paidAt: admin.firestore.FieldValue.serverTimestamp(),
      paymentDetails: {
        senderName: senderName || 'Unknown',
        rawNotification: rawNotification || '',
      }
    });

    console.log(`Order ${orderId} successfully updated to PAID.`);
    
    // Kirim response sukses
    return res.status(200).send({ 
      message: 'Payment verified successfully',
      orderId: orderId
    });

  } catch (error) {
    console.error('Error during payment verification:', error);
    return res.status(500).send({ message: 'Internal Server Error', error: error.message });
  }
});