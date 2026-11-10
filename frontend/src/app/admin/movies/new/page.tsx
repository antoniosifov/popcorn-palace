import { MovieForm } from "@/components/admin-forms";
import { TitleRow } from "@/components/ui";

export default function NewMoviePage() {
  return (
    <>
      <TitleRow title="Add a movie" />
      <MovieForm />
    </>
  );
}
