package io.github.sumyabazar1.weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fork: a built-in list of Indian cities, so a city is picked from a list with the remote's D-pad instead of typed
 * on an on-screen keyboard. Alphabetical by name; the coordinates are to about a kilometre, which is plenty for
 * a forecast. A city that is not here is still reachable through the "Another city" row (the original search).
 */
final class IndianCities {

    /** "Name|State|latitude|longitude". */
    private static final String[] RAW = {
            "Agartala|Tripura|23.83|91.28", "Agra|Uttar Pradesh|27.18|78.01", "Ahmedabad|Gujarat|23.03|72.58",
            "Ajmer|Rajasthan|26.45|74.64", "Akola|Maharashtra|20.71|77.00", "Aligarh|Uttar Pradesh|27.88|78.08",
            "Ambala|Haryana|30.38|76.78", "Amravati|Maharashtra|20.93|77.75", "Amritsar|Punjab|31.63|74.87",
            "Asansol|West Bengal|23.68|86.98", "Aizawl|Mizoram|23.73|92.72", "Ayodhya|Uttar Pradesh|26.80|82.20",
            "Ballari|Karnataka|15.14|76.92", "Bareilly|Uttar Pradesh|28.37|79.43", "Bathinda|Punjab|30.21|74.95",
            "Belagavi|Karnataka|15.85|74.50", "Bengaluru|Karnataka|12.97|77.59", "Bhagalpur|Bihar|25.24|86.97",
            "Bhavnagar|Gujarat|21.76|72.15", "Bhilai|Chhattisgarh|21.21|81.38", "Bhopal|Madhya Pradesh|23.26|77.41",
            "Bhubaneswar|Odisha|20.30|85.82", "Bikaner|Rajasthan|28.02|73.31", "Bilaspur|Chhattisgarh|22.08|82.15",
            "Chandigarh|Chandigarh|30.73|76.78", "Chennai|Tamil Nadu|13.08|80.27", "Coimbatore|Tamil Nadu|11.02|76.96",
            "Cuttack|Odisha|20.46|85.88", "Darjeeling|West Bengal|27.04|88.26", "Dehradun|Uttarakhand|30.32|78.03",
            "Delhi|Delhi|28.61|77.21", "Dhanbad|Jharkhand|23.80|86.43", "Durgapur|West Bengal|23.55|87.32",
            "Erode|Tamil Nadu|11.34|77.72", "Faridabad|Haryana|28.41|77.31", "Gandhinagar|Gujarat|23.22|72.65",
            "Gangtok|Sikkim|27.34|88.61", "Gaya|Bihar|24.80|85.00", "Ghaziabad|Uttar Pradesh|28.67|77.45",
            "Gorakhpur|Uttar Pradesh|26.76|83.37", "Guntur|Andhra Pradesh|16.31|80.44", "Gurugram|Haryana|28.46|77.03",
            "Guwahati|Assam|26.14|91.74", "Gwalior|Madhya Pradesh|26.22|78.18", "Haridwar|Uttarakhand|29.95|78.16",
            "Howrah|West Bengal|22.59|88.31", "Hubballi|Karnataka|15.36|75.12", "Hyderabad|Telangana|17.39|78.49",
            "Imphal|Manipur|24.82|93.94", "Indore|Madhya Pradesh|22.72|75.86", "Itanagar|Arunachal Pradesh|27.08|93.61",
            "Jabalpur|Madhya Pradesh|23.18|79.99", "Jaipur|Rajasthan|26.91|75.79", "Jalandhar|Punjab|31.33|75.58",
            "Jalgaon|Maharashtra|21.01|75.57", "Jammu|Jammu and Kashmir|32.73|74.87", "Jamnagar|Gujarat|22.47|70.06",
            "Jamshedpur|Jharkhand|22.80|86.20", "Jhansi|Uttar Pradesh|25.45|78.57", "Jodhpur|Rajasthan|26.29|73.02",
            "Junagadh|Gujarat|21.52|70.46", "Kakinada|Andhra Pradesh|16.99|82.25", "Kalaburagi|Karnataka|17.33|76.83",
            "Kannur|Kerala|11.87|75.37", "Kanpur|Uttar Pradesh|26.45|80.35", "Karnal|Haryana|29.69|76.99",
            "Karimnagar|Telangana|18.44|79.13", "Kochi|Kerala|9.93|76.27", "Kohima|Nagaland|25.67|94.11",
            "Kolhapur|Maharashtra|16.70|74.24", "Kolkata|West Bengal|22.57|88.36", "Kollam|Kerala|8.89|76.61",
            "Kota|Rajasthan|25.18|75.84", "Kozhikode|Kerala|11.26|75.78", "Latur|Maharashtra|18.40|76.58",
            "Leh|Ladakh|34.15|77.58", "Lucknow|Uttar Pradesh|26.85|80.95", "Ludhiana|Punjab|30.90|75.86",
            "Madurai|Tamil Nadu|9.93|78.12", "Mangaluru|Karnataka|12.91|74.86", "Mathura|Uttar Pradesh|27.49|77.67",
            "Meerut|Uttar Pradesh|28.98|77.71", "Moradabad|Uttar Pradesh|28.84|78.78", "Mumbai|Maharashtra|19.08|72.88",
            "Muzaffarpur|Bihar|26.12|85.39", "Mysuru|Karnataka|12.30|76.64", "Nagpur|Maharashtra|21.15|79.09",
            "Nanded|Maharashtra|19.14|77.32", "Nashik|Maharashtra|20.00|73.79", "Navi Mumbai|Maharashtra|19.03|73.03",
            "Nellore|Andhra Pradesh|14.44|79.99", "Nizamabad|Telangana|18.67|78.09", "Noida|Uttar Pradesh|28.54|77.39",
            "Ooty|Tamil Nadu|11.41|76.70", "Palakkad|Kerala|10.78|76.65", "Panaji|Goa|15.49|73.83",
            "Panipat|Haryana|29.39|76.97", "Patiala|Punjab|30.34|76.39", "Patna|Bihar|25.59|85.14",
            "Port Blair|Andaman and Nicobar|11.62|92.73", "Prayagraj|Uttar Pradesh|25.43|81.85",
            "Puducherry|Puducherry|11.93|79.83", "Pune|Maharashtra|18.52|73.86", "Raipur|Chhattisgarh|21.25|81.63",
            "Rajkot|Gujarat|22.30|70.80", "Ranchi|Jharkhand|23.34|85.31", "Ratnagiri|Maharashtra|16.99|73.30",
            "Rewa|Madhya Pradesh|24.53|81.30", "Rishikesh|Uttarakhand|30.09|78.27", "Rohtak|Haryana|28.90|76.61",
            "Rourkela|Odisha|22.26|84.85", "Sagar|Madhya Pradesh|23.84|78.74", "Salem|Tamil Nadu|11.66|78.15",
            "Sambalpur|Odisha|21.47|83.97", "Sangli|Maharashtra|16.85|74.57", "Shillong|Meghalaya|25.58|91.89",
            "Shimla|Himachal Pradesh|31.10|77.17", "Siliguri|West Bengal|26.73|88.40", "Solapur|Maharashtra|17.66|75.91",
            "Srinagar|Jammu and Kashmir|34.08|74.80", "Surat|Gujarat|21.17|72.83", "Thane|Maharashtra|19.22|72.98",
            "Thanjavur|Tamil Nadu|10.79|79.14", "Thiruvananthapuram|Kerala|8.52|76.94", "Thrissur|Kerala|10.53|76.21",
            "Tiruchirappalli|Tamil Nadu|10.79|78.69", "Tirupati|Andhra Pradesh|13.63|79.42",
            "Tiruppur|Tamil Nadu|11.11|77.34", "Udaipur|Rajasthan|24.59|73.71", "Ujjain|Madhya Pradesh|23.18|75.78",
            "Vadodara|Gujarat|22.31|73.18", "Varanasi|Uttar Pradesh|25.32|83.01", "Vellore|Tamil Nadu|12.92|79.13",
            "Vijayawada|Andhra Pradesh|16.51|80.65", "Visakhapatnam|Andhra Pradesh|17.69|83.22",
            "Warangal|Telangana|17.97|79.60",
    };

    /** The cities, sorted by name. */
    static final List<Forecast.Place> ALL = parse(RAW);

    private IndianCities() {
    }

    /** The state of a listed city, for a place saved before the state was kept; empty when it is not listed. */
    static String stateOf(String name) {
        for (Forecast.Place p : ALL) if (p.name.equalsIgnoreCase(name)) return p.detail;
        return "";
    }

    static List<Forecast.Place> parse(String[] raw) {
        List<Forecast.Place> cities = new ArrayList<>();
        for (String line : raw) {
            String[] f = line.split("\\|");
            cities.add(new Forecast.Place(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
        }
        Collections.sort(cities, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return Collections.unmodifiableList(cities);
    }
}
