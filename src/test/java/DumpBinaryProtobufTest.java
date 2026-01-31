import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DumpBinaryProtobufTest {

    @Test
    void loadDescriptorSetAndDecode() throws Exception {
        // Create minimal descriptor set: message TestMessage { string value = 1; }
        DescriptorProtos.FieldDescriptorProto field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("value")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .build();
        DescriptorProtos.DescriptorProto msgDesc = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("TestMessage")
                .addField(field)
                .build();
        DescriptorProtos.FileDescriptorProto fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .addMessageType(msgDesc)
                .build();
        DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet.newBuilder()
                .addFile(fileProto)
                .build();

        Path tempFile = Files.createTempFile("dump-binary-test", ".desc");
        try {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile.toFile())) {
                set.writeTo(fos);
            }
            List<Descriptors.Descriptor> descriptors = DumpBinaryProtobuf.loadDescriptorSet(tempFile);
            assertFalse(descriptors.isEmpty());
            assertEquals("TestMessage", descriptors.get(0).getName());

            Descriptors.Descriptor descriptor = descriptors.get(0);
            // Encode minimal message: value = "hello"
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
            builder.setField(descriptor.findFieldByName("value"), "hello");
            byte[] encoded = builder.build().toByteArray();

            String json = DumpBinaryProtobuf.decodeToJson(encoded, descriptor);
            assertNotNull(json);
            assertTrue(json.contains("hello"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void findMessageDescriptor() throws Exception {
        DescriptorProtos.DescriptorProto msgDesc = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("MyMessage")
                .build();
        DescriptorProtos.FileDescriptorProto fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("mypackage")
                .addMessageType(msgDesc)
                .build();
        DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet.newBuilder()
                .addFile(fileProto)
                .build();

        Path tempFile = Files.createTempFile("dump-binary-test", ".desc");
        try {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile.toFile())) {
                set.writeTo(fos);
            }
            List<Descriptors.Descriptor> descriptors = DumpBinaryProtobuf.loadDescriptorSet(tempFile);
            assertEquals(1, descriptors.size());

            assertNotNull(DumpBinaryProtobuf.findMessageDescriptor(descriptors, "MyMessage"));
            assertNotNull(DumpBinaryProtobuf.findMessageDescriptor(descriptors, "mypackage.MyMessage"));
            assertNotNull(DumpBinaryProtobuf.findMessageDescriptor(descriptors, null));
            assertNotNull(DumpBinaryProtobuf.findMessageDescriptor(descriptors, ""));
            assertNull(DumpBinaryProtobuf.findMessageDescriptor(descriptors, "Unknown"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
