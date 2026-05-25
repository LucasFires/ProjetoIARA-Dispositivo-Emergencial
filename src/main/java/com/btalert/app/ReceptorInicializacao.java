package com.btalert.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class ReceptorInicializacao extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences preferencias = context.getSharedPreferences(
                MainActivity.NOME_PREFERENCIAS, Context.MODE_PRIVATE);

        String contato  = preferencias.getString(MainActivity.CHAVE_NOME_CONTATO, null);
        String mensagem = preferencias.getString(MainActivity.CHAVE_MENSAGEM, "");

        if (contato != null && !mensagem.isEmpty()) {
            Intent intencaoServico = new Intent(context, ServicoBluetooth.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intencaoServico);
            } else {
                context.startService(intencaoServico);
            }
        }
    }
}
