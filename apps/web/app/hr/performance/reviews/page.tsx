import ReviewsClient from "./reviews-client";

export const dynamic = "force-dynamic";
export const revalidate = 0;

export default function ReviewsPage() {
  return <ReviewsClient />;
}
