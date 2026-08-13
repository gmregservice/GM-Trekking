package com.gmtrekking.app.crash

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Schermata mostrata quando l'app va in crash (vedi CrashHandler.kt).
 *
 * Scritta con le View classiche di Android, costruite da codice invece che da
 * un file di layout XML o da Compose: è intenzionale. Questa schermata deve
 * essere il più semplice e indipendente possibile dal resto dell'app (niente
 * tema personalizzato, niente risorse condivise), perché deve riuscire ad
 * aprirsi anche se il crash originale ha lasciato l'app in uno stato
 * imprevedibile. Aggiunta perché l'app viene compilata ed eseguita solo
 * tramite GitHub Actions, senza Android Studio: senza questa schermata, per
 * leggere l'errore di un crash servirebbe adb/Logcat.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE)
            ?: "Nessun dettaglio disponibile."

        val padding = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "GM-Trekking si è chiuso inaspettatamente"
            setTextColor(Color.BLACK)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Copia o condividi il testo qui sotto per segnalare il problema."
            setTextColor(Color.DKGRAY)
            textSize = 14f
            setPadding(0, padding / 2, 0, padding)
        }

        val errorText = TextView(this).apply {
            text = stackTrace
            setTextColor(Color.BLACK)
            textSize = 12f
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(errorText)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        val shareButton = Button(this).apply {
            text = "Condividi l'errore"
            setOnClickListener {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, stackTrace)
                }
                startActivity(Intent.createChooser(sendIntent, "Condividi l'errore"))
            }
        }

        val closeButton = Button(this).apply {
            text = "Chiudi"
            setOnClickListener { finishAffinity() }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, padding, 0, 0)
            addView(shareButton)
            addView(closeButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.WHITE)
            addView(title)
            addView(subtitle)
            addView(scrollView)
            addView(buttonRow)
        }

        setContentView(root)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }
}
