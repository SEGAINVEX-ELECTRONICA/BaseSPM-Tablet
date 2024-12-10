package com.segainvex.basespmtablet;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.os.Vibrator;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.UUID;
/************************************************************************
* En esta activity se programa la funcionalidad de la aplicación
 * en el layout conviven los controles de la base (motores Z) y
 * los de la cabeza. Lo que hago es hacer unos visibles o invisibles
 * según lo que se quiera controlar en cada momento.
* ***********************************************************************/
public class BaseActivity extends AppCompatActivity
{
    private static final String TAG = "BaseActivity"; //Para log en fase de depuración
    SharedPreferences preferencias;//Preferencias que se guardan permanentemente
    private Vibrator vibrator; //Para que vibren los pulsadores
    //Componentes comunes
    private TextView comando;
    private TextView respuesta;
    private Spinner nPasos;//Para listar los pasos discretos en la pantalla principal de la APP
    //Componentes de la cabeza
    ConstraintLayout componentesCabeza;
    LinearLayout graficoXY;
    GraficoFotodiodo graficoCabeza;
    private TextView fuerzaNormal;
    private TextView fuerzaLateral;
    private TextView suma;
    private Button  botonContinuoDiscreto;
    private ToggleButton cabezaUp,cabezaDown,cabezaRight,cabezaLeft;
    private Button botonLaserFotodiodo;
    private SeekBar velocidadCabeza;
    //Componentes de la base
    ConstraintLayout componentesBase;
    LinearLayout graficoZ;
    GraficoAcelerometro graficoBase;
    private TextView GX;//Para mostrar las coordenadas del acelerómetro
    private TextView GY;
    private ToggleButton subir, bajar;
    private SeekBar velocidadBase;
    private Switch z1;
    private Switch z2;
    private Switch z3;
    //Variables
    //boolean despulsadoPorSoftware =false;//Para saber si se despulsa por software o lo hace usuario
    private boolean modoContinuo=true;//Solo sirve para el botón botonContinuoDiscreto
    private int motorActivo;
    private int velocidadMotorBase;
    private int velocidadMotorCabeza;
    private boolean  movimientoDiscreto;//Para una cantidad de pasos discreta. Se actualiza con las preferencias
    private boolean baseActivaCabezaInactiva=true;//Para mostrar los controes de base o cabeza
    private int pasosMovimientoDiscreto;//Pasos a dar en movimiento discreto
    private boolean laserCabeza=true;
    //Componentes Bluetooth
    BluetoothAdapter miBluetoothAdapter =null;//La radio Bluetooth de aparato
    BluetoothDevice miDevice = null;//Bluetooth device que representa el dispositivo remoto
    BluetoothSocket miSocket = null;//Para enchufar el Bluetooth device a la radio Bluetooth
    private ConnectedThread miThread;//Hilo que lee y escribe la entrada del bluetooth
    Handler miHandler;//Para administrar los datos recibidos por Bluetooth en un handler
    //Esto se usa en la configuración para indicar al Bluetooth que va a funcionar como
    //un puerto serial (pero no estoy seguro)
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    /************************************************************
     *          onCreate de la Activity BaseActivity
    * ***********************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        Toolbar toolbar =  findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //Preferencias
        preferencias = PreferenceManager.getDefaultSharedPreferences(this);
        //Vibración de los botones
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        //Componentes comunes además del toolbar y el menú
        //..........Inicio de la gestión del spinner nPasos..........................
        nPasos = (Spinner)findViewById(R.id.n_pasos);//Lista con el número de pasos a dar en discreto
        ArrayAdapter<String> nPasosAdapter = new ArrayAdapter<String>(this, //ArrayAdapter del spinner
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.pasos));
        nPasos.setAdapter(nPasosAdapter);
        //Listener para seleccionar un valor del spinner nPasos y actualizar las preferencias
        nPasos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {//Métodos de la interfaz que hay que implementar
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id)
            {
                String seleccion = (String) parentView.getItemAtPosition(position).toString();
                SharedPreferences.Editor editor = preferencias.edit();//Objeto editor de preferencias
                editor.putString("pasos",seleccion);//Escribe la selección
                editor.apply();//Aplica el editor
            }
            @Override public void onNothingSelected(AdapterView<?> parentView){}});
        //Ahora establezco el valor por defecto del spinner nPasos
        String seleccion = preferencias.getString("pasos","100");//Lee el valor de las preferencias
        int nPasosPosicion = nPasosAdapter.getPosition(seleccion);//Encuentra la posición de ese valor
        nPasos.setSelection(nPasosPosicion);//Establece el valor encontrado en el spinner nPasos
        //..........Fin de la gestión del spinner nPAsos..........................
        botonContinuoDiscreto = (Button) findViewById(R.id.continuo_discreto);
        comando = (TextView) findViewById(R.id.comando);//Texto para ver el comando enviado
        respuesta = (TextView) findViewById(R.id.respuesta);//Texto con la respuesta de la base
        //Componentes de la cabeza
        componentesCabeza = findViewById(R.id.componentes_cabeza);//Layout con controles de la cabeza
        fuerzaNormal= (TextView) findViewById(R.id.fuerza_normal);//Para mostrar las señales del fotodiodo
        fuerzaLateral= (TextView) findViewById(R.id.fuerza_lateral);
        suma= (TextView) findViewById(R.id.suma);
        cabezaUp = (ToggleButton) findViewById(R.id.cabeza_up);//Botones de motores de la cabeza
        cabezaDown = (ToggleButton) findViewById(R.id.cabeza_down);
        cabezaRight = (ToggleButton) findViewById(R.id.cabeza_right);
        cabezaLeft = (ToggleButton) findViewById(R.id.cabeza_left);
        botonLaserFotodiodo = (Button) findViewById(R.id.laser_fotodiodo);
        velocidadCabeza = (SeekBar) findViewById(R.id.velocidad_cabeza);//Selección de velocidad
        //Gráfico de la cabeza
        graficoXY = (LinearLayout) findViewById(R.id.grafico_xy);
        graficoCabeza = new GraficoFotodiodo(this);
        graficoXY.addView(graficoCabeza);//Pone el gráfico en un Layout que va dentro del Layout principal
        //Componentes de la base
        componentesBase = findViewById(R.id.componentes_base);
        GX= (TextView) findViewById(R.id.textViewX);
        GY= (TextView) findViewById(R.id.textViewY);
        //enviar = (Button) findViewById(R.id.enviar);
        subir = (ToggleButton) findViewById(R.id.subir);
        bajar = (ToggleButton) findViewById(R.id.bajar);
        z1 = (Switch) findViewById(R.id.swZ1);
        z2 = (Switch) findViewById(R.id.swZ2);
        z3 = (Switch) findViewById(R.id.swZ3);
        //Velocidad de la base seekbar y métodos asociados
        velocidadMotorBase=Global.VELOCIDAD_INICIAL;//El seekBar debe estar en esa posición
        velocidadBase = (SeekBar) findViewById(R.id.velocidad_base);
        velocidadBase.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                //progress puede valer o, 1,2,3,4,5 y 6
                velocidadMotorBase=velocidadMotor(progress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}//Método no utilizado
            public void onStopTrackingTouch(SeekBar seekBar){}//Método no utilizado
        });
        //Velocidad de la cabeza seekbar y listeners asociados
        velocidadMotorCabeza=Global.VELOCIDAD_INICIAL;//El seekBar debe estar en esa posición
        velocidadCabeza = (SeekBar) findViewById(R.id.velocidad_cabeza);
        velocidadCabeza.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                //progress puede valer o, 1,2,3,4,5 y 6
                velocidadMotorCabeza=velocidadMotor(progress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar){}
        });
        // Grafica de la base
        graficoZ = (LinearLayout) findViewById(R.id.grafico_z);
        graficoBase = new GraficoAcelerometro(this);
        graficoZ.addView(graficoBase);
        // El botón de movimiento continuo/discreto hay que actualizarlo según las preferencias
        //guardadas
        movimientoDiscreto = preferencias.getBoolean("movimiento_discreto",false);
        if(movimientoDiscreto) {
            botonContinuoDiscreto.setText("discreto");
            nPasos.setVisibility(View.VISIBLE);//Si está en discreto muestra el spinner nPasos
            modoContinuo=false;
        }
            else {
            botonContinuoDiscreto.setText("continuo");
            nPasos.setVisibility(View.INVISIBLE);//Si está en continuo no muestra el spinner nPasos
            modoContinuo=true;
        }
        /*****************************************************************
         * Gestión del mensaje recibido
         * Handler para recibir mensajes del thread del bluetooth
        **************************************************************** */
        miHandler = new Handler()
        {
            public void handleMessage(android.os.Message msg)
            {
                String strRespuesta = "";//Para alojar la respuesta en un string
                //bytesRespuesta es un array de bits con la respuesta de Arduino
                byte[] bytesRespuesta = (byte[]) msg.obj;
                if (msg.what==Global.TipoRespuesta.SIN_FIRMA)//Si es una respuesta sin firma solo la muestra y sale
                {
                    try {
                        strRespuesta = new String(bytesRespuesta, "US-ASCII");
                        respuesta.setText(strRespuesta);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    return;//Muestra la respuesta sin firma y sale
                }//if
                //Si la respuesta es con firma quita la firma y la procesa
                int longResp = bytesRespuesta.length;//Longitud de la respuesta
                //La firma son dos caracteres y un espacio en blanco Ej.
                //Quitamos la firma de 2 caracteres a la respuesta y el espacio "respuestaTrim"
                byte[] respuestaTrim = Arrays.copyOfRange(bytesRespuesta, 3, longResp);
                int longRespuestaTrim = longResp-3;//Longitud de la respuesta trimada
                strRespuesta = new String(respuestaTrim);//Crea un String con los bytes de la respuesta
                respuesta.setText(strRespuesta);//Muestra la respuesta sin firma
                //Procesa la cadena recibida en función de la firma leida. msg.what infoma de
                //que tipo de respuesta es.
                switch (msg.what)//Posibles respuestas
                {
                    case Global.TipoRespuesta.FOTODIODO:
                        procesaFotodiodo(respuestaTrim,longRespuestaTrim);
                        break;
                    case Global.TipoRespuesta.ACELEROMETRO:
                        procesaAcelerometro(respuestaTrim,longRespuestaTrim);
                        break;
                    case Global.TipoRespuesta.LO_ENVIADO:
                        // TODO esto es lo que hay que poner en el TextViex "comando"
                        break;
                    case Global.TipoRespuesta.TEMPERATURA_HUMEDAD:
                        //temperaturaHumedad();TODO esto habría que implementarlo
                        break;
                    case Global.TipoRespuesta.VERSION:
                        //TODO indicar en algún sitio la versión del software del DUE
                        break;
                    case Global.TipoRespuesta.STOP:
                        desactivaBotonesCabezaBase();//Si recibo un stop de motores desacticvo botones
                        //respuesta.setText("motor parado");
                        break;
                }
            }
        };
    }//onCreate
   /**********************************************************************
     *                        Inflado del menú
     * *******************************************************************/
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_base, menu);
        return true; /** true -> el menú ya está visible */
    }
    /**********************************************************************
     * Gestión de items del menú
     **********************************************************************/
    @Override public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();
        if (id == R.id.salir) {System.exit(0); return true;}
        if (id == R.id.error) {pideError(null); return true;}
        if (id == R.id.cambio) {conmutaBaseCabeza(null); return true;}
        if (id == R.id.version) {version(null);return true;}
        if (id == R.id.acelerometro) {pideDatosAcelerometro(null);return true;}
        if (id == R.id.fotodiodo) {pideDatosFotodiodo(null);return true;}
        if (id == R.id.preferencias){lanzarPreferencias();return true;}
        if (id == R.id.busca_bluetooth){cambiaDeviceBluetooth(Global.NUEVO_BLUETOOTH);return true;}
        return super.onOptionsItemSelected(item);
    }
    /**********************************************************************
     * Métodos de BaseActivity
    **********************************************************************/
    /**********************************************************************
     * onResume. Aquí conectamos el bluetooth y
     * arrancamos el thred que administra el tráfico de datos
    **********************************************************************/
    @Override
    public void onResume()
    {
        super.onResume();
        //Aquí se llega con un device de los que están vinculados desde la BluetoothActivity
        //O desde onPause
        VerificarEstadoBT();//Si desde onPause se ha desactivado el bluetooth hay que verificarlo
        miDevice=Global.deviceBase;
        // Cancela cualquier  discovery en proceso
        miBluetoothAdapter.cancelDiscovery();//Cancel cualquier busqueda en proceso
        //Ahora hay que conectar
        try {//Como sin bluetooth no podemos seguir no hace falta un tread secundario
            miSocket = miDevice.createRfcommSocketToServiceRecord(BTMODULEUUID);//Creamos el socket
        } catch (IOException e) {e.printStackTrace();}
        try {
            miSocket.connect();//Conectamos el socket. Esto puede llevar un tiempo
        } catch (IOException e)
        {
            e.printStackTrace();
            cambiaDeviceBluetooth(Global.FALLO_CONEXION);//Resetea las preferencias y solicita un nuevo dispositivo
        }
        miThread = new ConnectedThread(miSocket,this,miHandler);//Tread para manejar el Bluetooth
        miThread.start();//Ejecuta el thread para administrar la conexión
    }
    /*********************************************************************
     * onPause. Aquí cerramos la conexión
     *********************************************************************/
    public void onPause()
    {
        super.onPause();
       // Cuando se sale de la aplicación esta parte permite que no se deje abierto el socket
        miThread.desconectaBluetooth();//Desenchufa el bluetooth
    }
    /*********************************************************************
    * MÉTODOS DE LA APLICACION
    **********************************************************************/
    /*********************************************************************
     * Metodo que envía a la base el comando de parada "MOT:MP 0"
     *********************************************************************/
    public void parar(View view)
    {
        enviaElComando("MOT:MP 0\r");
    }
    /*********************************************************************
     * Botón subir y bajar: Envía a la base el comando..
     * "MOT:MMP <MotorActivo Resolucion Frecuencia Sentido Pasos>"
     *********************************************************************/
    public void moverBase(ToggleButton view)
    {
            int sentido=0;
            //El  sentido depende del botón pulsado
            if(view.equals(bajar)) sentido=0; else sentido = 1;
            //Busca el motor que hay que activar según los switches de selección de motores Z
            motorActivo = motorBaseActivo();//Lee el motor seleccionado
            if(motorActivo==0) {
                Toast.makeText(this, "no hay motor seleccionado", Toast.LENGTH_SHORT).show();
                return;
            }
            //Preferencias. Tipo de movimiento y pasos discretos
            movimientoDiscreto = preferencias.getBoolean("movimiento_discreto",false);
            if(movimientoDiscreto)
            {
                String pasosDiscretos =  preferencias.getString("pasos","10000");
                pasosMovimientoDiscreto = Integer.parseInt(pasosDiscretos);
                enviaElComando("MOT:MMP " + motorActivo + " 256 " + velocidadMotorBase + " "+ sentido +" " + pasosMovimientoDiscreto +" \r");
            }
            else //Movimiento continuo
            {
                enviaElComando("MOT:MM " + motorActivo + " 256 " + velocidadMotorBase + " "+ sentido +"\r");
            }
        view.setChecked(true);//Cambia el aspecto del botón para distinguirlo
    }
    /********************************************************************
     * Método para seleccionar el motor activo a partir de los switches
    ******************************************************************** */
    private int motorBaseActivo()
    {
        //Lee el estado de los switeches
        Boolean z1On =  z1.isChecked();
        Boolean z2On =  z2.isChecked();
        Boolean z3On =  z3.isChecked();
        //Analiza el estado y devuelve el motor activo
        if(z1On && z2On && z3On) return 7;
        if(z1On && !z2On && !z3On) return 1;
        if(!z1On && z2On && !z3On) return 2;
        if(!z1On && !z2On && z3On) return 3;
        if(z1On && z2On && !z3On) return 4;
        if(z1On && !z2On && z3On) return 5;
        if(!z1On && z2On && z3On) return 6;
        if(!z1On && !z2On && !z3On) return 0;
        return 0;
    }
    /*********************************************************************
     * Metodo para parar motores.  Envía a la base el comando "MOT:MP 0"
     *********************************************************************/
    public void pararMotor()
    {
        //Si se ha despulsado un botón de la cabeza el usuario si se envía el comando
        enviaElComando("MOT:MP 0\r");
        //Desactiva todos los botones
        desactivaBotonesCabezaBase();
    }
    /*********************************************************************
     * Método que desactiva los pulsadores de la cabeza y de la base
    ********************************************************************* */
    private void desactivaBotonesCabezaBase()
    {
        cabezaUp.setActivated(false);
        cabezaDown.setActivated(false);
        cabezaRight.setActivated(false);
        cabezaLeft.setActivated(false);
        cabezaUp.setChecked(false);
        cabezaDown.setChecked(false);
        cabezaRight.setChecked(false);
        cabezaLeft.setChecked(false);
        subir.setChecked(false);
        bajar.setChecked(false);
        subir.setActivated(false);
        bajar.setActivated(false);
    }
    /**********************************************************************
     * Métodos llamdo cuando se pulsa el botón para mover el
     * fotodiodo o el laser. Cambia la variable laserCabeza, si
     * es true moverá el laser y si es false moverá el fotodiodo.
     * Primero para motor por si estuviera alguno en movimiento.
     **********************************************************************/
    public void laserFotodiodo(View view)
    {
        pararMotor();
        if(laserCabeza){
            laserCabeza=false;
            cabezaUp.setTextOn("fotodiodo");
            cabezaUp.setTextOff("fotodiodo");
            cabezaUp.setText("fotodiodo");
        }
        else{
            laserCabeza=true;
            cabezaUp.setTextOn("laser");
            cabezaUp.setTextOff("laser");
            cabezaUp.setText("laser");
        }
    }
    /**********************************************************************
     * Métodos llamdo cuando se pulsa un botón en la cabeza
     **********************************************************************/
    public void botonMoverCabeza(View botonPulsado)
    {
        if(botonPulsado.isActivated())//Si el botón esta activado hay que desactivarlo
        {
            pararMotor();//Primero para motores
            botonPulsado.setActivated(false);//Segundo desactiva el botón
        }
        else //S el botón estaba desactivado hay que pasarlo a activado
        {
            desactivaBotonesCabezaBase();//Desactiva todos los botones
            //botonPulsado.setActivated(true);//Activa solo el botón pulsado
            moverCabeza((ToggleButton) botonPulsado);//Pasamos el botón a la función moverCabeza
            botonPulsado.setActivated(true);//Ponenos dicho botón como activado
        }
    }
    /*********************************************************************
     * Método que atiende a los botones para mover motores de la cabeza
     *********************************************************************/
    public void moverCabeza(ToggleButton view)
    {
        int sentidoCabeza=0;
        view.setChecked(true);//Cambia el aspecto del botón para distinguirlo
        //Selección de motor y sentido
        if(view.equals(cabezaUp)){
            sentidoCabeza=1;
            if(laserCabeza)motorActivo=Global.laserY;
            else motorActivo=Global.fotodiodoY;
        }
        else if(view.equals(cabezaDown)){
            sentidoCabeza=0;
            if(laserCabeza)motorActivo=Global.laserY;
            else motorActivo=Global.fotodiodoY;
        }
        else if(view.equals(cabezaLeft)){
            sentidoCabeza=1;
            if (laserCabeza)motorActivo=Global.laserX;
            else motorActivo=Global.fotodiodoX;
        }
        else if(view.equals(cabezaRight)){
            sentidoCabeza=0;
            if (laserCabeza)motorActivo=Global.laserX;
            else motorActivo=Global.fotodiodoX;
        }
        //Preferencias. Tipo de movimiento y pasos a dar
        movimientoDiscreto = preferencias.getBoolean("movimiento_discreto",false);
        if(movimientoDiscreto)
        {
            String pasosDiscretos =  preferencias.getString("pasos","10000");
            pasosMovimientoDiscreto = Integer.parseInt(pasosDiscretos);
            enviaElComando("MOT:MMP " + motorActivo + " 256 " + velocidadMotorCabeza + " "+ sentidoCabeza +" " + pasosMovimientoDiscreto +" \r");
        }
        else //Movimiento continuo
        {
            enviaElComando("MOT:MM " + motorActivo + " 256 " + velocidadMotorCabeza + " "+ sentidoCabeza +"\r");
        }
    }
    /**********************************************************************
     * Métodos llamdo cuando se pulsa un botón en la base
     * Aquí se procesa la activacón o desactivación del botón
     * y se llama a pararMotor o a moverBase
     **********************************************************************/
    public void botonMoverBase(View botonPulsado)
    {
        if(botonPulsado.isActivated())//Si el botón esta activado hay que desactivarlo
        {
            pararMotor();//Primero para motores
            botonPulsado.setActivated(false);//Segundo desactiva el botón
        }
        else //Si el botón estaba desactivado hay que pasarlo a activado
        {
            desactivaBotonesCabezaBase();//Desactiva todos los botones
            botonPulsado.setActivated(true);//Activa solo el botón pulsado
            moverBase((ToggleButton) botonPulsado);//Pasamos el botón a la función moverBase
            botonPulsado.setActivated(true);//Ponenos dicho botón como activado
        }
    }
    /*********************************************************************
     * Procesa la respuesta del acelerómetro
     * *******************************************************************/
    public void procesaFotodiodo(byte[] respuestaBase,int largoRespuesta)
    {
        // Arduino DUE ha enviado sprintf(respuesta,"%s %.2f %.2f %.2f",FFOTODIODO,fn,fl,sum);
        //Como la firma se ha quitado hay 3 floats separados por 2 espacios que hay que
        //localizar
        int posicionEspacio1=0;
        int posicionEspacio2=0;

        for(int indice=0;indice < largoRespuesta-1;indice++)//Recorre el array de la respuesta...
        {
            if (respuestaBase[indice] == 32)//Busca los espacios y guarda sus posiciones
            {
                if (posicionEspacio1 == 0)
                    posicionEspacio1 = indice;
                else posicionEspacio2 = indice;
            }
        }
        //Extrae los valores numéricos en formato string
        String fn=new String(Arrays.copyOfRange(respuestaBase, 0, posicionEspacio1-1));
        String fl=new String(Arrays.copyOfRange(respuestaBase, posicionEspacio1+1,posicionEspacio2-1));
        String sum=new String(Arrays.copyOfRange(respuestaBase, posicionEspacio2+1, largoRespuesta-1));
        //Muestra los valores numéricos
        fuerzaNormal.setText("FN= "+ fn);
        fuerzaLateral.setText("FL= "+ fl);
        suma.setText("Sum= "+ sum);
        //Cambia del formato string a float
        float FN = Float.parseFloat(fn);
        float FL = Float.parseFloat(fl);
        float Sum = Float.parseFloat(sum);
        //Hace la gráfica
        graficoCabeza.cruces(FN,FL,Sum);
        graficoXY.removeView(graficoCabeza);//Borra el punto anterior (borra el gráfico completo)
        graficoXY.addView(graficoCabeza); //Pinta el gráfico de nuevo
    }
    /*********************************************************************
     * Procesa la respuesta del acelerómetro
     * *******************************************************************/
    public void procesaAcelerometro(byte[] respuesta, int largoRespuesta)
    {
        for(int indice=0;indice < largoRespuesta-1;indice++)//Recorre el array de la respuesta...
        {
            if(respuesta[indice]==32)//Buscando el caracter espacio
            {   //Cuando lo encuentra ya puede leer el valor de x
                String gx=new String(Arrays.copyOfRange(respuesta, 0, indice));
                String gy=new String(Arrays.copyOfRange(respuesta, indice+1, largoRespuesta-1));
                GY.setText("y= "+gy);
                GX.setText("x= "+gx);
                float x0 = Float.parseFloat(gx);
                float y0 = Float.parseFloat(gy);
                graficoBase.punto(x0,y0);
                graficoZ.removeView(graficoBase);//Borra el punto anterior (borra el gráfico completo)
                graficoZ.addView(graficoBase); //Pinta el gráfico con el nuevo punto
                break;//Salimos del for
            }
        }
    }
    /*********************************************************************
     * Estado del Bluetooth
     * Comprueba que el  Bluetooth está activado. Si no, regresa
     * a BluetoothActivity
     *********************************************************************/
    private void VerificarEstadoBT()
    {
        miBluetoothAdapter= BluetoothAdapter.getDefaultAdapter();
        if (miBluetoothAdapter.isEnabled())
        {
            Log.d(TAG, "...Bluetooth Activado...");//Está correcto
        }
        else //Si el bluetooth se ha desactivado vamos a  BluetoothActivity
        {
            finish();//Regresa a BluetoothActivity
            Log.d(TAG, "...Bluetooth Desactivado...");//Está correcto
        }
    }

    /**********************************************************************
     * Método que da servicio al botón de cambio de movimiento continuo a
     * discreto. Modifica el campo "movimiento_discreto" en las preferencias

     * ********************************************************************/
    public void cambiaContinuoDiscreto(View view)
    {
        if(modoContinuo)
        {
            botonContinuoDiscreto.setText("discreto");
            modoContinuo=false;
            SharedPreferences.Editor editor = preferencias.edit();
            editor.putBoolean("movimiento_discreto",true);
            editor.apply();
            nPasos.setVisibility(View.VISIBLE);//Si está en discreto muestra el spinner nPasos
        }
        else
        {
            botonContinuoDiscreto.setText("continuo");
            modoContinuo=true;
            SharedPreferences.Editor editor = preferencias.edit();
            editor.putBoolean("movimiento_discreto",false);
            nPasos.setVisibility(View.INVISIBLE);//Si no está en discreto oculta el spinner nPasos
            editor.apply();
        }
    }
    /*********************************************************************
     *                  Métodos del menú
    ******************************************************************* */
    /*********************************************************************
     * Item del menú preferencias: carga el fragment de preferencias
     *********************************************************************/
    public void lanzarPreferencias()
    {
        Intent intentPreferencias;
        intentPreferencias = new Intent(this, PreferenciasActivity.class);
        startActivity(intentPreferencias);
    }
    /*********************************************************************
     * Item del menú: Cambio entre activity de control de base o cabeza.
     * Primero para motor por si alguno estubiera en marcha.
     *********************************************************************/
    public void conmutaBaseCabeza(View view)
    {
        pararMotor();
        if(baseActivaCabezaInactiva) {
            componentesCabeza.setVisibility(View.VISIBLE);
            componentesBase.setVisibility(View.INVISIBLE);
            baseActivaCabezaInactiva=false;
        }
        else {
            componentesCabeza.setVisibility(View.INVISIBLE);
            componentesBase.setVisibility(View.VISIBLE);
            baseActivaCabezaInactiva=true;
        }
     }
    /*********************************************************************
     * Item del menú error: Pide error a la base
     *********************************************************************/
    public void pideError(View view)
    {
        enviaElComando("ERR?\r");
    }
    /*********************************************************************
     * Item del menú version: Pide la versión a la base
     *********************************************************************/
    public void version(View view)
    {
        enviaElComando("MOT:VER?\r");
    }
    /*********************************************************************
     * Item del menú acelerometro: Pide las coordendas del acelerómetro
     *********************************************************************/
    private void pideDatosAcelerometro(View view)
    {
        //Busca en preferencias el número de muestras a enviar
        String muestrasString = preferencias.getString("muestrasAcelerometro","100");
        int muestrasAcelerometro = Integer.parseInt(muestrasString);
        //Envía el comando
        enviaElComando("MOT:IAC "+ muestrasAcelerometro +" \r");
    }
    /*********************************************************************
     * Item del menú: fotodiodo: Pide las señales del fotodiodo
     *********************************************************************/
    private void pideDatosFotodiodo(View view)
    {
        //Busca en preferencias el número de muestras a enviar
        String muestrasString = preferencias.getString("muestrasFotodiodo","100");
        int muestrasFotodiodo = Integer.parseInt(muestrasString);
        enviaElComando("MOT:IFO "+ muestrasFotodiodo +" \r");
    }
    /*********************************************************************
     * Item del menú busca_bluetooth. Devuelve el control a la
     * actividad del Bluetooth
     ********************************************************************/
    private void cambiaDeviceBluetooth(int motivoCambio) {
        SharedPreferences.Editor editor = preferencias.edit();
        editor.putString("mac", "00:00:00:00:00:00");//Resetea la mac en preferencia
        editor.apply();
        //Devuelve el control a BluetoothActivity
        Intent intent = new Intent();
        setResult(motivoCambio, intent);
        finish();//Regresa al BluetoothActivity para que busque un nuevo device bluetooth
    }
    /*********************************************************************
     * Envía el comando al socket del bluetooht.
     * Recibe como argumento  un string con el comando
     *********************************************************************/
    private boolean enviaElComando(String comand)
    {
        vibrator.vibrate(Global.TIEMPO_VIBRACION);//Hace una vibración
        comando.setText(comand);//Muestra el comando
        miThread.write(comand.getBytes());//Envía el comando convertido a array de bytes
        return true;
    }
    /***********************************************************************
     * Establece la velocidad en función del seekbar de la base o de
     * la cabeza
     ********************************************************************* */
    private int velocidadMotor(int valorSeekbar) {
        int velocidad=1;//Por defecto 1 KHz
        switch(valorSeekbar) {
            case 0:
                velocidad = 1; //1KHz
                break;
            case 1:
                velocidad = 3; //3KHz
                break;
            case 2:
                velocidad = 5; //5KHz
                break;
            case 3:
                velocidad = 10; //10KHz
                break;
            case 4:
                velocidad = 20; //20KHz
                break;
            case 5:
                velocidad = 30; //30KHz
                break;
            case 6:
                velocidad = 60; //60KHz
                break;
            default: velocidad=1;break;
        }
        return velocidad;
    }
/***************************************************************************
****************************************************************************/
}//class

