package de.snowworks.ariana.xspace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.snowworks.ariana.ArianaDeviceApi

/**
 * Visible, local-only approval surface for one pending X Space proposal.
 * This activity is non-exported in the manifest.
 */
class XSpaceApprovalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val proposalId = intent.getStringExtra(EXTRA_PROPOSAL_ID)?.trim().orEmpty()
        if (proposalId.isEmpty()) {
            finish()
            return
        }

        val api = ArianaDeviceApi(this)
        val review = api.pendingXSpaceApprovals().firstOrNull { it.proposalId == proposalId }
        if (review == null) {
            Toast.makeText(this, "X-Space-Freigabe ist abgelaufen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val secondsLeft = ((review.expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        AlertDialog.Builder(this)
            .setTitle("Ariana X-88 · X Space")
            .setMessage(
                review.summary +
                    "\n\nAktion: ${review.action}" +
                    "\nNoch gültig: ${secondsLeft}s" +
                    "\n\nBestätigen führt genau diese angezeigte Aktion einmal aus.",
            )
            .setNegativeButton("Ablehnen") { _, _ ->
                val result = api.denyXSpaceAction(proposalId)
                Toast.makeText(
                    this,
                    if (result.optBoolean("ok", false)) "X-Space-Aktion abgelehnt." else "Freigabe war bereits abgelaufen.",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
            .setPositiveButton("Bestätigen") { _, _ ->
                val result = api.approveXSpaceAction(proposalId)
                Toast.makeText(
                    this,
                    if (result.optBoolean("ok", false)) "X-Space-Aktion bestätigt und ausgeführt."
                    else result.optString("message", "X-Space-Aktion konnte nicht ausgeführt werden."),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        private const val EXTRA_PROPOSAL_ID = "proposalId"

        fun launch(context: Context, proposalId: String) {
            val intent = Intent(context, XSpaceApprovalActivity::class.java)
                .putExtra(EXTRA_PROPOSAL_ID, proposalId)
            if (context !is AppCompatActivity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
