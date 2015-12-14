import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


public class MuzMachina implements MetaEventListener {

	JFrame ramkaGlowna;
	JPanel panelGlowny;
	JList listaOtrzymanych;
	JTextField komunikatUzytkownika;
	ArrayList<JCheckBox> listaPolWyboru;
	int nastepnyNum;
	Vector<String> wektorLista = new Vector<String>();
	String uzytkownik;
	ObjectOutputStream wyj;
	ObjectInputStream wej;
	HashMap<String, boolean[]> mapaOdebranychKompozycji = new HashMap<String, boolean[]>();
	
	Sequencer sekwenser;
	Sequence sekwencja;
	Sequence mojaSekwencja = null;
	Track sciezka;
	
	String[] nazwyInstrumentow = {"Bass Drum", "Closed Hi-Hat", "Open Hi-Hat","Acoustic Snare",
			"Crash Cymbal", "Hand Clap", "High Tom", "Hi Bongo", "Maracas",
			"Whistle", "Low Conga", "Cowbell", "Vibraslap", "Low-mid Tom",
			"High Agogo", "Open Hi Conga"};
	
			int[] instrumenty = {35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};
	
	public static void main(String[] args)  {
		new MuzMachina().konfigurujAplk(args[0]); // args[0] zawiera Twoja nazwe lub identyfikator
	}
	
	public void konfigurujAplk(String nazwa){
		uzytkownik = nazwa;
		// nawiazujemy polaczenie z serwerem
		try {
		Socket sock = new Socket("127.0.0.1", 4242);
		wyj = new ObjectOutputStream(sock.getOutputStream());
		wej = new ObjectInputStream(sock.getInputStream());
		Thread watekZd = new Thread(new ZdalnyCzytelnik());
		watekZd.start();
		} catch(Exception ex) {
		System.out.println("Brak połączenia - będziesz musial grac sam.");
		}
		konfigurujMidi();
		tworzGUI();
		}// koniec konfiguracji
	
		public void tworzGUI(){
		ramkaGlowna = new JFrame("MuzMachina");
		ramkaGlowna.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		BorderLayout uklad = new BorderLayout();
		JPanel panelTla = new JPanel(uklad);
		panelTla.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		
		listaPolWyboru = new ArrayList<JCheckBox>();
		
		Box obszarPrzyciskow = new Box(BoxLayout.Y_AXIS);
		JButton start = new JButton("Start");
		start.addActionListener(new MojStartListener());
		obszarPrzyciskow.add(start);
		
		JButton stop = new JButton("Stop");
		stop.addActionListener(new MojStopListener());
		obszarPrzyciskow.add(stop);
		
		JButton tempoG = new JButton("Szybciej");
		tempoG.addActionListener(new MojTempoGListener());
		obszarPrzyciskow.add(tempoG);
		
		JButton tempoD = new JButton("Wolniej");
		tempoD.addActionListener(new MojTempoDListener());
		obszarPrzyciskow.add(tempoD);
		
		JButton wyslij = new JButton("Wyslij");
		wyslij.addActionListener(new MojWyslijListener());
		obszarPrzyciskow.add(wyslij);
		
		komunikatUzytkownika = new JTextField();
		obszarPrzyciskow.add(komunikatUzytkownika);
		
		listaOtrzymanych = new JList();
		listaOtrzymanych.addListSelectionListener(new WyborZListyListener());
		listaOtrzymanych.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane lista = new JScrollPane(listaOtrzymanych);
		obszarPrzyciskow.add(lista);
		listaOtrzymanych.setListData(wektorLista); // na poczatku brak danych
		
		Box obszarNazw = new Box(BoxLayout.Y_AXIS);
		for (int i = 0; i < 16; i++) {
			obszarNazw.add(new Label(nazwyInstrumentow[i]));
		}
		
		panelTla.add(BorderLayout.EAST, obszarPrzyciskow);
		panelTla.add(BorderLayout.WEST, obszarNazw);
		
		ramkaGlowna.getContentPane().add(panelTla);
		GridLayout siatkaPolWyboru = new GridLayout(16,16);
		siatkaPolWyboru.setVgap(1);
		siatkaPolWyboru.setHgap(2);
		panelGlowny = new JPanel(siatkaPolWyboru);
		panelTla.add(BorderLayout.CENTER, panelGlowny);
	
		for (int i = 0; i < 256; i++) {
			JCheckBox c = new JCheckBox();
			c.setSelected(false);
			listaPolWyboru.add(c);
			panelGlowny.add(c);
			} // koniec pętli
		
		ramkaGlowna.setBounds(50,50,300,300);
		ramkaGlowna.pack();
		ramkaGlowna.setVisible(true);
	} // koniec metody
		
	public void konfigurujMidi() {
		try {
			sekwenser = MidiSystem.getSequencer();
			sekwenser.open();
			sekwenser.addMetaEventListener(this);
			sekwencja = new Sequence(Sequence.PPQ,4);
			sciezka = sekwencja.createTrack();
			sekwenser.setTempoInBPM(120);
			} catch(Exception e) {e.printStackTrace();}
		} // koniec metody
	
	public void utworzSciezkeIOdtworz() {
		ArrayList<Integer> listaSciezki = null; // ta tablica bÚdzie zawieraÊ instrumenty
		sekwencja.deleteTrack(sciezka);
		sciezka = sekwencja.createTrack();
		
		for (int i = 0; i < 16; i++) {
			
			listaSciezki = new ArrayList<Integer>();
		
				for (int j = 0; j < 16; j++ ) {
					JCheckBox jc = (JCheckBox) listaPolWyboru.get(j + (16*i));
					if (jc.isSelected()) {
					int key = instrumenty[i];
					listaSciezki.add(new Integer(key));
					} else {
						listaSciezki.add(null); // gdyz ten wpis sciezki powinien byc pusty
					}
				} // koniec pętli wewnetrznej for
			utworzSciezke(listaSciezki);
		} // koniec pętli zewnętrznej
		sciezka.add(tworzZdarzenie(192,9,1,0,15)); // - zawsze mamy peïne 16 taktów	
		try {
			sekwenser.setSequence(sekwencja);
			sekwenser.setLoopCount(sekwenser.LOOP_CONTINUOUSLY);
			sekwenser.start();
			sekwenser.setTempoInBPM(120);
			} catch(Exception e) {e.printStackTrace();}
		} // koniec metody
		
		 class MojStartListener implements ActionListener {
			public void actionPerformed(ActionEvent a) {
			utworzSciezkeIOdtworz();
			}
		} // koniec klasy wewnętrznej
		
		class MojStopListener implements ActionListener {
			public void actionPerformed(ActionEvent a) {
			sekwenser.stop();
			}
		} // koniec klasy wewnÚtrznej
		
			class MojTempoGListener implements ActionListener {
				public void actionPerformed(ActionEvent a) {
					float wspTempa = sekwenser.getTempoFactor();
					sekwenser.setTempoFactor((float) ((float)wspTempa * 1.03));
				}
			} // koniec klasy wewnętrznej
			
			 class MojTempoDListener implements ActionListener {
					public void actionPerformed(ActionEvent a) {
						float wspTempa = sekwenser.getTempoFactor();
						sekwenser.setTempoFactor((float) ((float)wspTempa * .97));
					}//koniec metody
			} // koniec klasy wewnętrznej
			 
			public class MojWyslijListener implements ActionListener {
				 public void actionPerformed(ActionEvent a) {
				 // tworzymy tablice ze stanami pol wyboru
				 boolean[] stanPolaWyboru = new boolean[256];
				 for (int i = 0; i < 256; i++) {
					 JCheckBox pole = (JCheckBox) listaPolWyboru.get(i);
					 if(pole.isSelected()){
						 stanPolaWyboru[i] = true;
					 }
				 }//koniec petli for
				 String komunikatDoWyslania = null;
				 try {
					 wyj.writeObject(uzytkownik + nastepnyNum++ + ": " + komunikatUzytkownika.getText());
					 wyj.writeObject(stanPolaWyboru);
				 } catch(Exception ex) {
				System.out.println("Przykro mi bracie. Nie mogïem wysïaÊ kompozycji na serwer.");
				 }
				 komunikatUzytkownika.setText("");	 
				}//koniec metody
			}// koniec klasy wewnetrznej
			
			public class WyborZListyListener implements ListSelectionListener {
				public void valueChanged(ListSelectionEvent le) {
					if (!le.getValueIsAdjusting()) {
						String wybranaOpcja = (String) listaOtrzymanych.getSelectedValue();
						if (wybranaOpcja != null) {
							// a teraz, wrcamy do mapy i zmieniamy sekwencjÚ
							boolean[] stanZaznaczonego = (boolean[]) mapaOdebranychKompozycji.get(wybranaOpcja);
							zmienSekwencje(stanZaznaczonego);
							sekwenser.stop();
							utworzSciezkeIOdtworz();
						}
					}
				}//koniec metody
			}//koniec klasy wewnetrznej
			
			public class ZdalnyCzytelnik implements Runnable {
				boolean[] stanPolaWyboru = null;
				String prezentowanaNazwa = null;
				Object obj = null;
						
				public void run() {
					try {
						while((obj=wej.readObject()) != null) {
							System.out.println("pobraliĂmy obiekt z serwera");
							System.out.println(obj.getClass());
							String nazwaDoWyswietlenia = (String) obj;
							stanPolaWyboru = (boolean[]) wej.readObject();
							mapaOdebranychKompozycji.put(nazwaDoWyswietlenia, stanPolaWyboru); 
							wektorLista.add(nazwaDoWyswietlenia);
							listaOtrzymanych.setListData(wektorLista);
						} // koniec while
					} catch(Exception ex) {ex.printStackTrace();}
				} // koniec metody run
			} // koniec klasy wewnetrznej
			
			public class OdtworzMojeListener implements ActionListener {
				public void actionPerformed(ActionEvent a) {
					if (mojaSekwencja != null) {
						sekwencja = mojaSekwencja; // przywracamy mój oryginał
					}
				} // koniec metody
			} // koniec klasy wewnętrznej
			
			public void zmienSekwencje(boolean[] stanPolaWyboru) {
				for (int i = 0; i < 256; i++) {
				JCheckBox pole = (JCheckBox) listaPolWyboru.get(i);
				if (stanPolaWyboru[i]) {
				pole.setSelected(true);
				} else {
				pole.setSelected(false);
			}
		} // koniec pętli for
	} // koniec metody
			
			public void utworzSciezke(ArrayList list) {
				Iterator iter = list.iterator();
					for (int i = 0; i < 16; i++) {
						Integer num = (Integer) iter.next();
						if (num != null) {
							int numKlaw = num.intValue();
							sciezka.add(tworzZdarzenie(144,9,numKlaw, 100, i));
							sciezka.add(tworzZdarzenie(128,9,numKlaw,100, i + 1));
						}
					} // koniec petli for
			} // koniec metody
			
			public MidiEvent tworzZdarzenie(int plc, int kanal, int jeden, int dwa, int takt) {
				MidiEvent zdarzenie = null;
				try {
				ShortMessage a = new ShortMessage();
				a.setMessage(plc, kanal, jeden, dwa);
				zdarzenie = new MidiEvent(a, takt);
				} catch(Exception e) { }
				return zdarzenie;
			} // koniec metody

			@Override
			public void meta(MetaMessage arg0) {
				// TODO Auto-generated method stub
				
			}
		}// koniec klasy	
			
			
		
	


		
