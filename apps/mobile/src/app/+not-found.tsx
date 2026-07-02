import { Link } from "expo-router";
import { StyleSheet, Text, View } from "react-native";

export default function NotFoundScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>المسار غير متوفر</Text>
      <Link href="/" style={styles.link}>العودة إلى SNAD CRM</Link>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 16,
    padding: 24,
    backgroundColor: "#F5F7FB",
  },
  title: {
    color: "#14213D",
    fontSize: 24,
    fontWeight: "800",
  },
  link: {
    color: "#174EA6",
    fontSize: 16,
    fontWeight: "700",
  },
});
