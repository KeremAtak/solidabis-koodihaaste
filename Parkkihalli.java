import java.util.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

class Parkkihalli {
  public static void main(String[] args) {
    System.out.print(new Parkkihalli().toString());
  }
  
  private static List<TuloJaLahtoAika> tuloJaLahtoAjat = new ArrayList<>();
  private Tilastolaskuri laskuri;
  
  public Parkkihalli() {
    for (int i = 0; i < 50000; i++) {
      tuloJaLahtoAjat.add(new TuloJaLahtoAika());
    }
    Tilastolaskuri laskuri = new Tilastolaskuri(tuloJaLahtoAjat, 6, 9, 3, "2018-09-15 09:00:00");
    setLaskuri(laskuri);
  }

  public List<TuloJaLahtoAika> getTimeStamps() {
    return tuloJaLahtoAjat;
  }
  
  public String toString() {
	return laskuri.toString();
  }
  
  public void setLaskuri(Tilastolaskuri laskuri) {
	  this.laskuri = laskuri;
  }
  
  /**
   * Tilastolaskuri on koodihaasteen pääluokka, se suorittaa koodihaasteen pääasiallisen laskennan
   */
  public class Tilastolaskuri {
	  //List<TuloJaLahtoAika> järjestetään tuloaikojen mukaan
	  private List<TuloJaLahtoAika> jarjestetytTuloJaLahtoAjat;
	  //Oheinen taulukko sisältää tiedon parkkiruuduista: ruutu on tyhjä tai se kertoo mikä auto ruudussa on
	  private TuloJaLahtoAika[] autotParkkihallissa;
	  //Oheinen jono kertoo, missä järjestyksessä autot poistuvat tallista
	  private PriorityQueue<Date> poistuvienAutojenJono;
	  //Lista kaikista viikoista
	  private List<Viikko> viikot = new ArrayList<>();
	  private int autojenLkmParkkihallissa, parkkihallinKapasiteetti, leveys, pituus, kerrostenLkm;
	  //Viimeinen kellonaika: tehtävänannossa 2018-09-15 09:00:00.
	  private String viimeinenKlo;
	  //Ilmentymämuuttujaan ei tallenneta parkkimaksuja, vaan minuutteja
	  private long minuutitYhteensa;
	  
	  public Tilastolaskuri(List<TuloJaLahtoAika> tuloJaLahtoAjat, int pituus, int leveys, int kerrostenLkm, String viimeinenKlo) {
		  this.pituus = pituus;
		  this.leveys = leveys;
		  this.kerrostenLkm = kerrostenLkm;
		  this.parkkihallinKapasiteetti = pituus * leveys * kerrostenLkm;
		  this.autojenLkmParkkihallissa = 0;
		  this.viimeinenKlo = viimeinenKlo;
		  this.minuutitYhteensa = 0;
		  this.autotParkkihallissa = new TuloJaLahtoAika[this.parkkihallinKapasiteetti];
		  this.poistuvienAutojenJono = new PriorityQueue<Date>(parkkihallinKapasiteetti,(a,b) -> a.compareTo(b));
		  this.jarjestetytTuloJaLahtoAjat = tuloJaLahtoAjat;
		  Collections.sort(jarjestetytTuloJaLahtoAjat);
		  simuloiParkkihallia();
	  }
	  
	  /**
	   * Kun tämä metodi on suoritettu loppuun, tulokset ovat valmiita tulostettaviksi.
	   */
	  public void simuloiParkkihallia() {
		  poistaTurhatSaapumiset();
		  Date alku = jarjestetytTuloJaLahtoAjat.get(0).getArrivalTime();
		  Date loppu = new Date(alku.getTime() + (7 * 24 * 3600 * 1000 - 1000));
		  
		  int viikkolaskuri = 1;
		  //Lisätään ensimmäinen viikko, viikot alkavat ensimmäisenä tulevan auton 
		  //sisääntuloajasta ja päättyvät viikkoa myöhemmin.
		  viikot.add(new Viikko(alku, loppu, 0, viikkolaskuri));
		  
		  int i = 0;
		  //Käydään kaikki halutun aikavälin autot läpi
		  while (i < jarjestetytTuloJaLahtoAjat.size()) {
			  //Jos viikko on päättynyt, luodaan uusi viikko
			  if (jarjestetytTuloJaLahtoAjat.get(i).getArrivalTime().after(loppu)) {
				  viikot.get(viikot.size() - 1).setIndeksi2(i - 1);
				  viikkolaskuri++;
				  alku = new Date(loppu.getTime() + 1000);
				  loppu = new Date(alku.getTime() + 7 * 24 * 3600 * 1000 - 1000);
				  
				  //Rajatapaus: tulevalla viikolla autoja ei tule. Tällöin hypätään viikon yli kokonaan.
				  while (loppu.before(jarjestetytTuloJaLahtoAjat.get(i).getArrivalTime())) {
					  alku = new Date(loppu.getTime() + 1000);
					  loppu = new Date(alku.getTime() + 7 * 24 * 3600 * 1000 - 1000);
					  viikkolaskuri++;
				  }
				  
				  viikot.add(new Viikko(alku, loppu, i, viikkolaskuri));
			  }
			  if (poistuvienAutojenJono.size() == 0) {
				  lisaaAutoHalliin(i, 0);
				  i++;
			  } else {
				  if (poistuvienAutojenJono.peek().after(jarjestetytTuloJaLahtoAjat.get(i).getArrivalTime())) {
					  tarkistaMahtuukoAutoHalliin(i);
					  i++;
				  } else {
					  poistaAutoHallista();
				  }
			  }
		  }
		  kasitteleLopputilanne(i);
	  }
	  
	  /**
	   * Metodissa auto lisätään parkkihalliin
	   */
	  public void lisaaAutoHalliin(int tuloJaLahtoAikojenIndeksi, int parkkihallinIndeksi) {
		  autotParkkihallissa[parkkihallinIndeksi] = jarjestetytTuloJaLahtoAjat.get(tuloJaLahtoAikojenIndeksi);
		  poistuvienAutojenJono.add(jarjestetytTuloJaLahtoAjat.get(tuloJaLahtoAikojenIndeksi).getDepartureTime());
		  autojenLkmParkkihallissa++;
		  minuutitYhteensa += laskeKertyvatMinuutit(jarjestetytTuloJaLahtoAjat.get(tuloJaLahtoAikojenIndeksi));
	  }
	  
	  /**
	   * Metodi tarkastaa onko parkkihallissa tilaa, ja lisää auton halliin jos tilaa on.
	   * Muussa tapauksessa se laskee menetetyt tulot ja merkkaa auton tyhjäksi
	   */
	  public void tarkistaMahtuukoAutoHalliin(int i) {
		  if (autojenLkmParkkihallissa == parkkihallinKapasiteetti) {
			  viikot.get(viikot.size() - 1).lisaaMenetettyjaMinuutteja(laskeKertyvatMinuutit(jarjestetytTuloJaLahtoAjat.get(i)));
			  //Listattu auto merkitään tyhjäksi, jotta sitä ei huomioida myöhemmässä laskennassa
			  jarjestetytTuloJaLahtoAjat.set(i, null);
		  } else {
			  int j = 0;
			  //Autoilijat elävät ideaalissa maailmassa, joten he täyttävät paikat järjestyksessä
			  while (autotParkkihallissa[j] != null) {
				  j++;
			  }
			  lisaaAutoHalliin(i, j);
		  }
	  }
	  
	  /**
	   * Metodi poistaa jonossa ensimmäisenä olevan auton hallista
	   */
	  public void poistaAutoHallista() {
		  asetaParkkiruutuTyhjaksi();
		  poistuvienAutojenJono.poll();
		  autojenLkmParkkihallissa--;
	  }
	  
	  /**
	   * Metodi etsii parkkiruudun mikä vastaa jonon ensimmäistä autoa, ja asettaa sen tyhjäksi
	   */
	  public void asetaParkkiruutuTyhjaksi() {
		  int i = 0;
		  while (true) {
			  if (autotParkkihallissa[i] != null) {
				  if (autotParkkihallissa[i].getDepartureTime() == poistuvienAutojenJono.peek()) {
					  break;
				  }
			  }
			  i++;
		  }
		  autotParkkihallissa[i] = null;
	  }
	  
	  /**
	   * Kun metodia kutsutaan, kaikki autot mitkä halusivat tulla sisään ennen haluttua 
	   * päivämäärää on käsitelty.
	   * Metodissa viimeistellään viimeinen viikko ja lasketaan jokaisen viikon käyttöaste.
	   */
	  public void kasitteleLopputilanne(int i) {
		  viimeisteleViimeinenViikko(i);
		  for (int j = 0; j < viikot.size(); j++) {
			  viikot.get(j).laskeKayttoaste(jarjestetytTuloJaLahtoAjat, parkkihallinKapasiteetti);
		  }
	  }
	  
	  /**
	   * Metodi käsittelee viimeisen viikon loppuun asettamalla sille oikeita arvoja
	   */
	  public void viimeisteleViimeinenViikko(int i) {
		  Date timestamp = new Date(Timestamp.valueOf(viimeinenKlo).getTime());
	      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	      dateFormat.format(timestamp);
	      //Rajatapaus: viimeisellä viikolla ei tule, eikä lähde autoja
	      if (jarjestetytTuloJaLahtoAjat.get(i - 1) != null) {
	    	  if (jarjestetytTuloJaLahtoAjat.get(i - 1).getDepartureTime().before(timestamp)) {
		    	  Date oldTimestamp = timestamp;
		    	  timestamp = jarjestetytTuloJaLahtoAjat.get(i - 1).getDepartureTime();
		    	  if (timestamp.after(oldTimestamp)) {
		    		  timestamp = oldTimestamp;
		    	  }
		      }
	      }
    	  viikot.get(viikot.size() - 1).setPvm2(timestamp);
		  viikot.get(viikot.size() - 1).setIndeksi2(i - 1);
	  }
	  
	  /**
	   * Metodi palauttaa tiedon siitä, kuinka monta minuuttia auto viettää aikaa parkkihallissa
	   */
	  public long laskeKertyvatMinuutit(TuloJaLahtoAika t) {
		  long minuutit = (t.getDepartureTime().getTime() - t.getArrivalTime().getTime()) / 1000 / 60;
		  //Vajaat minuutit veloitetaan
		  minuutit = minuutit % 60 == 0 ? minuutit : minuutit + 1 ;
		  return minuutit;
	  }
	  
	  /**
	   * Metodi poistaa listasta kaikki turhat TuloJaLahtoAika-oliot, eli ne jotka tulevat halutun
	   * päivämäärän jälkeen (tehtävänannossa 2018-09-15 09:00:00).
	   * Koska jarjestetytTuloJaLahtoAjat on järjestetty saapumispäivämäärien mukaan, poistettavia
	   * olioita ei tarvitse hakea, vaan lista voidaan käsitellä lopusta alkuun kunnes päädytään ei-
	   * poistettavaan olion.
	   */
	  public void poistaTurhatSaapumiset() {
		  Long timestampTime = Timestamp.valueOf(viimeinenKlo).getTime();
		  Date timestamp = new Date(timestampTime);
	      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	      dateFormat.format(timestamp);
	      
		  int i = this.jarjestetytTuloJaLahtoAjat.size() - 1;
		  while (true) {
			  if (i == 0) {
				  break;
			  }
			  if ((this.jarjestetytTuloJaLahtoAjat.get(i).arrival).before(timestamp)) {
				  break;
			  }
			  this.jarjestetytTuloJaLahtoAjat.remove(i);
			  i--;
		  }
	  }
	  
	  /**
	   * Metodi palauttaa hallin merkkijonona
	   */
	  public String tulostaParkkihalli() {
		  String palautus = "";
		  palautus += "Parkkihallin mallinnus\n(A = auto, X = tyhjä)\nAutoilijat täyttävät ruudut vasemmalta oikealle ja ylhäältä alas\n";
		  for (int i = 0; i < kerrostenLkm; i++) {
			  for (int l = 0; l < pituus; l++) {
				  palautus += "-";
			  }
			  palautus += "\nKerros " + (i + 1) + "\n";
			  for (int l = 0; l < pituus; l++) {
				  palautus += "-";
			  }
			  palautus += '\n';
			  for (int j = 0; j < leveys; j++) {
				  for (int k = 0; k < pituus; k++) {
					  if (autotParkkihallissa[k + pituus * j + leveys * pituus * i] == null) {
						  palautus += "X";
					  } else {
						  palautus += "A";
					  }
  				  }
				  palautus += '\n';
			  }
		  }
		  return palautus;
	  }
	  
	  /**
	   * Metodi palauttaa viikot merkkijonona
	   */
	  public String viikotMerkkijonona() {
		  String palautus = "";
		  for (int i = 0; i < viikot.size(); i++) {
			  palautus += viikot.get(i);
		  }
		  return palautus;
	  }
	  
	  public String toString() {
		  return  "Autoja parkissa: " + autojenLkmParkkihallissa + "\n\n" 
				  + "Parkkimaksuja kertynyt: " + String.format("%.2f", minuutitYhteensa * 0.05) + "€\n\n"
				  + tulostaParkkihalli() + '\n'
		  		  + viikotMerkkijonona();
	  }
  }
  
  /**
   * Viikko sisältää tiedon viikon aloituspäivämäärästä, lopetuspäivämäärästä, tiedon minkä kahden indeksin
   * välillä viikon autot sijaitsevat jarjestetytTuloJaLahtoAjat:ssa, viikon numeron, kuinka monta minuuttia
   * hukattiin ja käyttöasteen prosentuaalisesti.
   * Lisäksi viikko laskee käyttöasteensa ja palauttaa viikon merkkijonona.
   */
  public class Viikko {
	  
	  private Date pvm1;
	  private Date pvm2;
	  private int indeksi1;
	  private int indeksi2;
	  private int viikonNro;
	  private long menetetytMinuutit;
	  private double kayttoaste;
	  
	  public Viikko(Date pvm1, Date pvm2, int indeksi1, int viikonNro) {
		  this.pvm1 = pvm1;
		  this.pvm2 = pvm2;
		  this.indeksi1 = indeksi1;
		  this.viikonNro = viikonNro;
		  this.menetetytMinuutit = 0;
		  this.kayttoaste = 0;
	  }
	  
	  /**
	   * Metodi laskee viikon käyttöasteen.
	   */
	  public void laskeKayttoaste(List<TuloJaLahtoAika> ajat, int parkkihallinKapasiteetti) {
		  long kaytettyAika = 0;
		  korjaaIndeksi(ajat);
		  kaytettyAika = laskeKaytettyAika(ajat);
		  
		  kayttoaste = (kaytettyAika * 1.0 / (( pvm2.getTime() - pvm1.getTime()) * parkkihallinKapasiteetti)) * 100;
	  }
	  
	  /**
	   * Metodi korjaa aloitusindeksin siten, että myös alkupäässä kaikki ne autot jotka edes osittain 
	   * kuuluvat viikkoon lasketaan mukaan viikkoon.
	   * Esimerkiksi jos viikko on "2018-08-08 00:00:00" - "2018-08-14 23:59:59", niin auto joka parkkeeraa
	   * aikana "2018-08-14 22:00:00" - "2018-08-15 00:01:00" lasketaan osaksi viikkoa.
	   */
	  public void korjaaIndeksi(List<TuloJaLahtoAika> ajat) {
		  while (true) {
			  if (indeksi1 == 0) {
				  break;
			  }
			  //Laskennassa ei huomioida niitä autoja, jotka eivät mahtuneet parkkihalliin; nämä autot 
			  //ovat merkitty tyhjiksi listassa
			  if (ajat.get(indeksi1 - 1) != null) {
				  if (pvm1.after(ajat.get(indeksi1 - 1).getDepartureTime())) {
					  break;
				  }
			  }
			  indeksi1--;
		  }
	  }
	  
	  /**
	   * Metodi laskee kuinka paljon autot viettivät viikon aikana yhteensä aikaa parkkihallissa.
	   * Oikeellisen laskennan vuoksi metodi korjaa tarvittaessa kunkin auton kellonaikoja sitten, että vain
	   * ne kellonajat jotka sisältyvät viikkoon lasketaan käytetyksi ajaksi.
	   */
	  public long laskeKaytettyAika(List<TuloJaLahtoAika> ajat) {
		  Date saapumisaika;
		  Date lahtemisaika;
		  long palautus = 0;
		  for (int i = indeksi1; i <= indeksi2; i++) {
			  //Laskennassa ei huomioida niitä autoja, jotka eivät mahtuneet parkkihalliin; nämä autot 
			  //ovat merkitty tyhjiksi listassa
			  if (ajat.get(i) != null) {
				  saapumisaika = ajat.get(i).getArrivalTime();
				  lahtemisaika = ajat.get(i).getDepartureTime();
				  //Jos auto saapui halliin ennen viikon alkua, saapumisaika on viikon ensimmäinen kellonaika.
				  if (saapumisaika.before(pvm1)) {
					  saapumisaika = pvm1;
				  }
				  //Jos auto lähti hallista ennen viikon loppua, lähtemisaika on viikon viimeinen kellonaika.
				  if (lahtemisaika.after(pvm2)) {
					  lahtemisaika = pvm2;
				  }
				  palautus += lahtemisaika.getTime() - saapumisaika.getTime();
			  }
		  }
		  return palautus;
	  }
	  
	  public Date getPvm1() {
		  return pvm1;
	  }
	  
	  public Date getPvm2() {
		  return pvm1;
	  }
	  
	  public void setPvm2(Date pvm2) {
		  this.pvm2 = pvm2;
	  }
	  
	  public long getMenetetytMinuutit() {
		  return menetetytMinuutit;
	  }
	  
	  public void lisaaMenetettyjaMinuutteja(long menetetytMinuutit) {
		  this.menetetytMinuutit += menetetytMinuutit;
	  }
	  
	  public void setIndeksi2(int indeksi2) {
		  this.indeksi2 = indeksi2;
	  }
	  
	  public double getKayttoaste() {
		  return kayttoaste;
	  }
	  
	  
	  public String toString() {
		  return  "---------------------------------------\n\n" +
				  "Viikko " + viikonNro + " (" + pvm1 + "-" + pvm2 +  ")\n"
				  + "Viikon käyttöaste: " + String.format("%.2f", kayttoaste) + "%\n"
				  + "Viikon menetetyt tulot: " + String.format("%.2f", menetetytMinuutit * 0.05) + "€\n\n";
	  }
  }
  
  public class TuloJaLahtoAika implements Comparable<TuloJaLahtoAika> {
    private Date arrival;
    private Date departure;
	
    public TuloJaLahtoAika() {
      Long minArrivalTime = Timestamp.valueOf("2018-08-01 00:00:00").getTime();
      Long maxDepartureTime = Timestamp.valueOf("2018-09-30 00:00:00").getTime();
      Long randomTimeBetweenMaxAndMin = minArrivalTime + (long)(Math.random() * (maxDepartureTime - minArrivalTime + 1));
	  
      arrival = new Date(randomTimeBetweenMaxAndMin);
      departure = new Date(maxDepartureTime);
	  
      while ((departure).after(new Date(maxDepartureTime - 1))) {
        departure = new Date((long)(randomTimeBetweenMaxAndMin + new Random().nextGaussian() * (120 * 60000) + 360 * 60000));
      }
	  
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
      dateFormat.format(arrival);
      dateFormat.format(departure);
    }
	
    private Date getArrivalTime() {
      return arrival;
    }
	
    private Date getDepartureTime() {
      return departure;
    }
	
    public String toString() {
    	return "saapumisaika: " + arrival + " lähtöaika: " + departure + System.getProperty("line.separator");
    }
    
    public int compareTo(TuloJaLahtoAika tuloJaLahtoAika) {
    	return this.getArrivalTime().compareTo(tuloJaLahtoAika.getArrivalTime());
    }
  }
}
            
