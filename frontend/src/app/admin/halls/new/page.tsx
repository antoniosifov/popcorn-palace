import { HallForm } from "@/components/admin-forms";
import { TitleRow } from "@/components/ui";

export default function NewHallPage() {
  return (
    <>
      <TitleRow title="Add a hall" />
      <HallForm />
    </>
  );
}
