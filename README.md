### \# Projeto IARA - Dispositivo Emergencial Vestível



##### \## Como Funciona?



O funcionamento do sistema é focado na redução do tempo de resposta em casos de extrema urgência (como quedas ou crises de saúde):



1\. **Acionamento**: O usuário pressiona o botão principal na pulseira inteligente.



2\. **Conexão Sem Fio**: A pulseira (via Bluetooth) envia um sinal imediato para o smartphone pareado.



3\. **Processamento**: O aplicativo móvel intercepta o sinal, e busca a lista de contatos configurada.



4\. **Notificação**: O sistema dispara automaticamente um alerta de emergência via SMS (através de uma API) para os contatos de emergência.



\---//---



##### \## Especificações Técnicas (Hardware)



O circuito eletrônico foi projetado com foco em ergonomia, leveza e eficiência energética, utilizando os seguintes componentes:



* **Microcontrolador**: ESP32-C3 Super Mini (responsável pela lógica interna e conectividade Bluetooth estável).
* **Módulo de Carga**: TP4056 (controla o processo de carregamento e protege a bateria contra sobrecarga/descarga profunda).
* **Alimentação**: Bateria de lítio recarregável de 3,7V com interface de recarga via cabo Micro USB.
* **Componentes de Interface**: Botão físico texturizado (gatilho de pânico) e LEDs seletores para feedback visual de status.
* **Carcaça**: Modelada em ambiente 3D (*Fusion* 360) e impressa em filamento ABS (Acrilonitrila Budadieno Estireno), garantindo rigidez contra impactos e leveza no pulso.



\---//---



##### \## Escopo do Software e Lógica da Aplicação



A arquitetura de software do ecossistema IARA foi dividida em duas frentes fundamentais para garantir a usabilidade:



* **Firmware (ESP32-C3)**: Desenvolvido e otimizado para manter o gerenciamento de energia eficiente, garantindo conectividade ativa mesmo em modo standby, além de rotinas de anti-debounce no botão.
* **Aplicação Mobile (Android)**: Centraliza as funções críticas de configuração do sistema através de interfaces acessíveis (no máximo 5 cliques).
* **Gerenciamento de Contatos**: Cadastrar, consultar, atualizar e deletar os números de telefone e nomes dos cuidadores/responsáveis.
* **Editar Informações**: Permite a personalização da mensagem padrão de socorro e inclusão de dados pessoais essenciais (como endereço fixo).



\---//---



##### \## Metodologia e Testes de Validação



O ciclo de desenvolvimento seguiu uma abordagem prática e iterativa:



1\. **Prototipagem em Breadboard**: Validação da lógica inicial de comunicação Bluetooth serial com ferramentas de teste (como o *Serial Bluetooth Terminal*).

2\. **Refinamento de Hardware**: Soldagem com fluxo em placa de circuito para mitigar problemas de mau contato com a bateria e reforço de pontos críticos.

3\. **Testes de Campo**: Avaliação de estabilidade de sinal em modo econômico, testes de resistência a quedas, monitoramento de autonomia de bateria e validação de usabilidade com feedbacks reais de idosos e familiares.



\---//---



##### \## Como usar o APP

1. **Ative o Bluetooth** no celular
2. Abra o APP **IaraLink**
3. Conceda todas as permissões solicitadas
4. Toque em **Adicionar** → selecione o contato que receberá o SMS
5. Digite a **mensagem** no campo de texto e toque em **Salvar Mensagem**
6. Pressione o botão no ESP32 → o SMS é enviado automaticamente!



\---//---
